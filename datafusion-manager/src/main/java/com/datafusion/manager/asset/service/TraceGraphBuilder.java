package com.datafusion.manager.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.manager.asset.config.ResourceSyncConfig;
import com.datafusion.manager.asset.dto.builder.ResourceSnapshotBuilder;
import com.datafusion.manager.asset.dto.skywalking.CallEdge;
import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import com.datafusion.manager.asset.dto.skywalking.SpanDto;
import com.datafusion.manager.asset.dto.skywalking.SpanRefDto;
import com.datafusion.manager.asset.dto.skywalking.TagDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SkyWalking 调用链数据解析服务（血缘解析核心引擎）.
 * 负责将原始的 Span 列表转换为具有业务含义的服务间调用边（CallEdge），并提取关联的指标标签。
 *
 * @author zhengjiexiang
 * @version 1.0.1, 2026/04/16
 * @since 2025/10/22
 */
@Slf4j
@Service
public class TraceGraphBuilder {

    /** 资源同步配置. */
    @Autowired
    private ResourceSyncConfig resourceSyncConfig;

    /**
     * 静态映射表：用于将特定的 IP:Port 地址强制映射为标准服务名.
     */
    private static final Map<String, String> PEER_ADDRESS_TO_SERVICE_MAP;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("172.88.1.26:8082", "secp-data-center-api");
        PEER_ADDRESS_TO_SERVICE_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 构建调用链图的核心切入方法.
     * 逻辑包含：索引建立、双向标签汇总（向上及向下）、业务入口溯源、路径规范化及边合并。
     *
     * @param spanList SkyWalking 原始 Span 列表
     * @return 跨服务的调用边集合（已去重并合并标签）
     */
    public Set<CallEdge> buildCallGraph(List<SpanDto> spanList) {
        if (CollectionUtil.isEmpty(spanList)) {
            return Collections.emptySet();
        }

        Map<String, SpanDto> allSpansMap = new HashMap<>();
        Map<String, List<String>> childrenMap = new HashMap<>();
        Map<String, String> exitToEntryMap = new HashMap<>();

        // 1. 初始化索引：构建父子关系及跨 Segment 关联
        for (SpanDto span : spanList) {
            String key = generateSpanKey(span.getSegmentId(), span.getSpanId());
            allSpansMap.put(key, span);
            if (span.getParentSpanId() != -1) {
                String parentKey = generateSpanKey(span.getSegmentId(), span.getParentSpanId());
                childrenMap.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(key);
            }
            if (CollectionUtil.isNotEmpty(span.getRefs())) {
                for (SpanRefDto ref : span.getRefs()) {
                    String parentExitKey = generateSpanKey(ref.getParentSegmentId(), ref.getParentSpanId());
                    exitToEntryMap.put(parentExitKey, key);
                    childrenMap.computeIfAbsent(parentExitKey, k -> new ArrayList<>()).add(key);
                }
            }
        }

        // 2. 预计算：向上汇总所有子树的指标标签（性能优化：使用缓存避免重复递归）
        Map<String, Set<MetricsTagDto>> subtreeTagsCache = new HashMap<>();
        for (SpanDto span : spanList) {
            calculateSubtreeTagsRecursive(generateSpanKey(span.getSegmentId(), span.getSpanId()), allSpansMap, childrenMap, subtreeTagsCache);
        }

        // 3. 跨服务上下文对齐：记录 Entry Span 与其父级 Exit Span 的对应关系，用于提取路径模板
        Map<String, SpanDto> childSpanContextMap = new HashMap<>();
        for (SpanDto span : spanList) {
            if ("Entry".equalsIgnoreCase(span.getType()) && CollectionUtil.isNotEmpty(span.getRefs())) {
                for (SpanRefDto ref : span.getRefs()) {
                    childSpanContextMap.put(generateSpanKey(ref.getParentSegmentId(), ref.getParentSpanId()), span);
                }
            }
        }

        // 4. 核心逻辑：处理 Exit Span，生成调用边
        Map<String, CallEdge> mergedEdgeMap = new HashMap<>();
        for (SpanDto exitSpan : spanList) {
            // 仅处理出口（Exit）类型的 HTTP 调用
            if ("Exit".equalsIgnoreCase(exitSpan.getType()) && "Http".equalsIgnoreCase(exitSpan.getLayer())) {
                String calleeService = resolveServiceName(exitSpan);
                if (isIgnoredService(calleeService)) {
                    continue;
                }

                // 递归溯源：找到触发此出口调用的最终业务入口（Controller层）
                SpanDto realCallerSpan = findRealBusinessCaller(exitSpan, allSpansMap);
                if (realCallerSpan != null && isRealBusinessEntry(realCallerSpan)) {
                    String exitKey = generateSpanKey(exitSpan.getSegmentId(), exitSpan.getSpanId());
                    Set<MetricsTagDto> finalTags = new HashSet<>();

                    // 双向追溯指标：
                    // A. 向上追溯祖先节点的标签
                    Map<String, Set<MetricsTagDto>> ancestorTagsMap = traceMetricToAncestors(exitSpan, allSpansMap);
                    for (Set<MetricsTagDto> tags : ancestorTagsMap.values()) {
                        finalTags.addAll(tags);
                    }
                    // B. 向下获取子树（异步调用、本地调用）的标签
                    Set<MetricsTagDto> subtreeTags = subtreeTagsCache.get(exitKey);
                    if (subtreeTags != null) {
                        finalTags.addAll(subtreeTags);
                    }

                    // 规范化端点名称：包括 BasePath 补全和 {apiPath} 变量替换
                    String callerEndpoint = normalizeEndpointName(realCallerSpan.getServiceCode(), realCallerSpan, childSpanContextMap);
                    String calleeEndpoint = normalizeEndpointName(calleeService, exitSpan, childSpanContextMap);

                    CallEdge edge = new CallEdge(realCallerSpan.getServiceCode(), callerEndpoint,
                            calleeService, calleeEndpoint, "Http",
                            exitSpan.getEndTime() - exitSpan.getStartTime(),
                            finalTags.isEmpty() ? null : finalTags);

                    // 合并策略：基于 UniqueId（Caller+Callee组合）合并边并聚合 TagSet
                    if (mergedEdgeMap.containsKey(edge.getUniqueId())) {
                        CallEdge exist = mergedEdgeMap.get(edge.getUniqueId());
                        if (edge.getTagSet() != null) {
                            if (exist.getTagSet() == null) {
                                exist.setTagSet(new HashSet<>());
                            }
                            exist.getTagSet().addAll(edge.getTagSet());
                        }
                    } else {
                        mergedEdgeMap.put(edge.getUniqueId(), edge);
                    }
                }
            }
        }
        return new HashSet<>(mergedEdgeMap.values());
    }

    /**
     * 规范化端点名称.
     * 处理 Feign 模板、路径变量替换（如 {apiPath} 转真实路径）及 BasePath 补全逻辑。
     *
     * @param serviceCode 服务英文名
     * @param span        当前处理的 Span
     * @param context     跨 Segment 上下文映射
     * @return 格式化后的端点（如 POST:/api/biz-data/user/info）
     */
    private String normalizeEndpointName(String serviceCode, SpanDto span, Map<String, SpanDto> context) {
        final String method = span.getTags().stream().filter(t -> "http.method".equalsIgnoreCase(t.getKey())).map(TagDto::getValue).findFirst()
                .orElse("POST");
        String url = span.getTags().stream().filter(t -> "url".equalsIgnoreCase(t.getKey())).map(TagDto::getValue).findFirst().orElse("");
        String endpoint = span.getEndpointName();
        String spanKey = generateSpanKey(span.getSegmentId(), span.getSpanId());

        // 1. 跨服务关联：如果是出口调用，尝试获取被调用方真实的接口模板名
        if (context.containsKey(spanKey)) {
            endpoint = context.get(spanKey).getEndpointName();
        }

        // 2. 特殊变量转换：如果路径中包含 {apiPath}，则从真实 URL 中解析出具体路径
        if (resourceSyncConfig.getTransRealPathMap().containsKey(serviceCode)
                && resourceSyncConfig.getTransRealPathMap().get(serviceCode).contains(endpoint)) {
            endpoint = normalizeRealPath(url);
        }

        // 3. 提取纯路径：剥离协议及前缀
        String purePath = endpoint;
        if (purePath.contains(":/")) {
            purePath = purePath.substring(purePath.indexOf(":/") + 2);
        }
        if (purePath.startsWith("/")) {
            purePath = purePath.substring(1);
        }

        // 4. BasePath 补全：根据 Nacos 配置为服务增加 API 网关前缀
        String basePath = resourceSyncConfig.getBasePathMap().get(serviceCode);
        if (StringUtils.isNotBlank(basePath)) {
            String bp = basePath.startsWith("/") ? basePath.substring(1) : basePath;
            if (!purePath.startsWith(bp)) {
                purePath = (bp.endsWith("/") ? bp : bp + "/") + purePath;
            }
        }

        return method + ":/" + (purePath.startsWith("/") ? purePath.substring(1) : purePath);
    }

    /**
     * 从完整 URL 中解析路径部分.
     */
    private String normalizeRealPath(String url) {
        try {
            return new URI(url).getPath();
        } catch (URISyntaxException e) {
            return url;
        }
    }

    /**
     * 递归计算子树的所有指标标签.
     */
    private Set<MetricsTagDto> calculateSubtreeTagsRecursive(String key, Map<String, SpanDto> all, Map<String, List<String>> children,
            Map<String, Set<MetricsTagDto>> cache) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Set<MetricsTagDto> tags = new HashSet<>(extractMetricTags(all.get(key)));
        List<String> cList = children.get(key);
        if (cList != null) {
            for (String ck : cList) {
                tags.addAll(calculateSubtreeTagsRecursive(ck, all, children, cache));
            }
        }
        cache.put(key, tags);
        return tags;
    }

    /**
     * 向上追溯所有祖先节点的指标标签.
     */
    private Map<String, Set<MetricsTagDto>> traceMetricToAncestors(SpanDto span, Map<String, SpanDto> all) {
        Map<String, Set<MetricsTagDto>> res = new HashMap<>();
        traceAncestorRecursive(span, all, res);
        return res;
    }

    /**
     * 祖先追溯辅助递归方法.
     */
    private void traceAncestorRecursive(SpanDto span, Map<String, SpanDto> all, Map<String, Set<MetricsTagDto>> res) {
        if (span == null) {
            return;
        }
        Set<MetricsTagDto> tags = extractMetricTags(span);
        if (!tags.isEmpty()) {
            res.put(generateSpanKey(span.getSegmentId(), span.getSpanId()), tags);
        }
        if (span.getParentSpanId() != -1) {
            traceAncestorRecursive(all.get(generateSpanKey(span.getSegmentId(), span.getParentSpanId())), all, res);
        }
        if (CollectionUtil.isNotEmpty(span.getRefs())) {
            traceAncestorRecursive(all.get(generateSpanKey(span.getRefs().get(0).getParentSegmentId(), span.getRefs().get(0).getParentSpanId())), all,
                    res);
        }
    }

    /**
     * 从 Span Tag 中提取业务指标标签（tag + dimension）.
     * 使用 Nacos 配置中的时间维度映射表进行标准化。
     */
    private Set<MetricsTagDto> extractMetricTags(SpanDto span) {
        if (span == null || CollectionUtil.isEmpty(span.getTags())) {
            return Collections.emptySet();
        }
        String d = null;
        String t = null;
        for (TagDto tag : span.getTags()) {
            if ("metric.dimension".equals(tag.getKey())) {
                d = resourceSyncConfig.getTimeDimensionMap().get(tag.getValue());
            }
            if ("metric.tags".equals(tag.getKey())) {
                t = tag.getValue();
            }
        }
        if (StringUtils.isBlank(t)) {
            return Collections.emptySet();
        }
        Set<MetricsTagDto> res = new HashSet<>();
        for (String s : t.split(",")) {
            if (StringUtils.isNotBlank(s)) {
                res.add(new MetricsTagDto(s.trim(), d));
            }
        }
        return res;
    }

    /**
     * 解析被调用方服务名称.
     * 逻辑：硬编码映射 -> K8s DNS提取 -> 原始 Peer.
     *
     * @param span 调用链 Span
     * @return 服务名称
     */
    public String resolveServiceName(SpanDto span) {
        String p = span.getPeer();
        if (StringUtils.isBlank(p)) {
            return "UNKNOWN";
        }
        if (PEER_ADDRESS_TO_SERVICE_MAP.containsKey(p)) {
            return PEER_ADDRESS_TO_SERVICE_MAP.get(p);
        }
        // 处理 K8s 内部域名格式：prod-service-name.secp.svc...
        if (p.contains("prod-")) {
            int s = p.indexOf("prod-") + 5;
            return p.substring(s, p.indexOf(".", s));
        }
        return p;
    }

    /**
     * 递归寻找真实的业务发起者（即最顶层的入口 Controller Span）.
     */
    private SpanDto findRealBusinessCaller(SpanDto s, Map<String, SpanDto> all) {
        if (isRealBusinessEntry(s)) {
            return s;
        }
        if (s.getParentSpanId() != -1) {
            SpanDto p = all.get(generateSpanKey(s.getSegmentId(), s.getParentSpanId()));
            if (p != null) {
                return findRealBusinessCaller(p, all);
            }
        }
        if (CollectionUtil.isNotEmpty(s.getRefs())) {
            SpanDto p = all.get(generateSpanKey(s.getRefs().get(0).getParentSegmentId(), s.getRefs().get(0).getParentSpanId()));
            if (p != null) {
                return findRealBusinessCaller(p, all);
            }
        }
        return s;
    }

    /**
     * 判断是否为有效的业务入口点.
     * 排除消息队列消费者、内部线程切换等技术噪音，保留 HTTP 接口。
     */
    private boolean isRealBusinessEntry(SpanDto s) {
        if (s == null || !"Entry".equalsIgnoreCase(s.getType())) {
            return false;
        }
        String n = s.getEndpointName();
        // 过滤 MQ 消费等异步入口
        return n != null && !n.contains("Kafka/") && !n.contains("Consumer/")
                && ("Http".equalsIgnoreCase(s.getLayer()) || n.contains("/") || n.contains(":/"));
    }

    /**
     * 过滤中间件噪音服务（ES、阿里云组件等）.
     */
    private boolean isIgnoredService(String s) {
        if (s == null) {
            return true;
        }
        String l = s.toLowerCase();
        return l.contains("elasticsearch") || l.contains("aliyuncs") || l.contains(":9200") || "unknown".equals(l);
    }

    /**
     * 生成 Span 的唯一检索 Key.
     */
    private String generateSpanKey(String sid, Object pid) {
        return sid + ":" + pid;
    }

    /**
     * 从 spans 中解析 weLocation 列表，并将整个 Trace 链路中追溯到的指标标签聚合到入口位置.
     *
     * @param spans span 列表
     * @return weLocation 列表（包含聚合后的 tagSet）
     */
    public Set<ResourceSnapshotBuilder.WeLocation> parseWeLocations(List<SpanDto> spans) {
        if (CollectionUtil.isEmpty(spans)) {
            return new HashSet<>();
        }

        // --- 第一步：聚合整个 Trace 链路中所有 Span 的业务指标标签 (向上追溯) ---
        Set<MetricsTagDto> allTraceMetricTags = new HashSet<>();
        for (SpanDto span : spans) {
            Set<MetricsTagDto> spanTags = extractMetricTags(span);
            if (CollectionUtil.isNotEmpty(spanTags)) {
                allTraceMetricTags.addAll(spanTags);
            }
        }

        // --- 第二步：寻找入口 Span (带有 we-location) 并绑定聚合后的标签集 ---
        Set<ResourceSnapshotBuilder.WeLocation> weLocationSet = new HashSet<>();

        for (SpanDto spanDto : spans) {
            Optional<TagDto> tagDto = spanDto.getTags().stream()
                    .filter(tag -> "http.headers".equalsIgnoreCase(tag.getKey()))
                    .findFirst();

            if (tagDto.isPresent()) {
                String headersValue = tagDto.get().getValue();
                String[] lines = headersValue.split("\n");

                String projectName = null;
                String weLocation = null;

                for (String line : lines) {
                    if (line.startsWith("project-name=[")) {
                        projectName = line.substring("project-name=[".length(), line.length() - 1);
                    } else if (line.startsWith("we-location=[")) {
                        String url = line.substring("we-location=[".length(), line.length() - 1);
                        try {
                            String decodedUrl = java.net.URLDecoder.decode(url, "UTF-8");
                            int fragmentIndex = decodedUrl.indexOf("/#/");
                            if (fragmentIndex != -1 && fragmentIndex < decodedUrl.length() - 3) {
                                weLocation = decodedUrl.substring(fragmentIndex + 3);
                            }
                        } catch (Exception e) {
                            log.warn("解码 URL 时发生异常: {}", url, e);
                        }
                    }
                }

                // 标准化 weLocation
                if (weLocation != null) {
                    weLocation = normalizeWeLocation(weLocation);
                }

                if (projectName != null && weLocation != null) {
                    ResourceSnapshotBuilder.WeLocation wl = new ResourceSnapshotBuilder.WeLocation();
                    wl.setProjectName(projectName);
                    wl.setWeLocation(weLocation);

                    // 将整个 Trace 追溯聚合到的所有指标标签绑定到该入口
                    // 使用 new HashSet 复制一份，防止引用覆盖
                    wl.setTagSet(new HashSet<>(allTraceMetricTags));

                    weLocationSet.add(wl);
                }
            }
        }

        return weLocationSet;
    }

    /**
     * 标准化weLocation.
     * 1. 去除?后面的字符串
     * 2. 左边添加"/"
     * 3. 去除末尾的数字ID（/后面全是数字）
     *
     * @param weLocation 原始weLocation
     * @return 标准化后的weLocation
     */
    public String normalizeWeLocation(String weLocation) {
        if (StringUtils.isBlank(weLocation)) {
            return weLocation;
        }
        String path = weLocation.split("\\?")[0];
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // 正则：匹配 /数字 结构并截断后续所有字符
        return path.replaceAll("/[0-9]+.*", "");
    }

}
