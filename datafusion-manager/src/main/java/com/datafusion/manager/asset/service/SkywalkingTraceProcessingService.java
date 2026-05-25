package com.datafusion.manager.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.config.ResourceSyncConfig;
import com.datafusion.manager.asset.dao.AssetResourceMapper;
import com.datafusion.manager.asset.dto.builder.ResourceSnapshotBuilder;
import com.datafusion.manager.asset.dto.skywalking.CallEdge;
import com.datafusion.manager.asset.dto.skywalking.EndpointDto;
import com.datafusion.manager.asset.dto.skywalking.SkyWalkingServiceDto;
import com.datafusion.manager.asset.dto.skywalking.SpanDto;
import com.datafusion.manager.asset.dto.skywalking.TagDto;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.util.Strings;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * API服务调用链解析（血缘解析）.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/27
 * @since 2025/10/27
 */
@Slf4j
@Service
public class SkywalkingTraceProcessingService {

    /**
     * Skywalking GraphQL客户端.
     */
    private final SkywalkingGraphqlClient graphqlService;

    /**
     * 血缘解析服务.
     */
    private final TraceGraphBuilder traceGraphBuilder;

    /**
     * Redisson客户端.
     */
    private final RedissonClient redissonClient;

    /**
     * 临时表服务.
     */
    private final AssetResourceMapper assetResourceMapper;

    /**
     * 锁的租约时间：如果任务挂掉，锁会在10分钟后自动释放.
     */
    private static final long LOCK_LEASE_TIME_MINUTES = 100;

    /**
     * 尝试获取锁的等待时间，0秒，不等待，立即失败.
     */
    private static final long LOCK_WAIT_TIME_SECONDS = 0;

    /**
     * 分布式锁KEY的前缀.
     */
    private static final String LOCK_PREFIX = "api_link_update_lock:";

    /**
     * Skywalking时间格式.
     */
    private static final DateTimeFormatter SKYWALKING_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");

    /**
     * 数据库日期格式.
     */
    private static final DateTimeFormatter DB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 服务缓存Key前缀.
     */
    private static final String SERVICE_CACHE_PREFIX = "skywalking:service:";

    /**
     * 服务缓存过期时间：7天.
     */
    private static final long SERVICE_CACHE_TTL_DAYS = 7;

    /**
     * api同步配置.
     */
    @Autowired
    private ResourceSyncConfig resourceSyncConfig;

    /**
     * 构造函数.
     *
     * @param graphqlService      Skywalking GraphQL客户端
     * @param traceGraphBuilder   血缘解析服务
     * @param redissonClient      Redisson客户端
     * @param assetResourceMapper mapper
     */
    @Autowired
    public SkywalkingTraceProcessingService(SkywalkingGraphqlClient graphqlService,
            TraceGraphBuilder traceGraphBuilder,
            RedissonClient redissonClient,
            AssetResourceMapper assetResourceMapper) {
        this.graphqlService = graphqlService;
        this.traceGraphBuilder = traceGraphBuilder;
        this.redissonClient = redissonClient;
        this.assetResourceMapper = assetResourceMapper;
    }

    /**
     * 处理API资源链路分析任务.
     *
     * @param serviceEnName 服务英文名
     * @param startDate     开始日期
     * @param endDate       结束日期
     * @param sampleCount   每个耗时区间采样数量
     * @param hourSplit     间隔多长时间踩一次样本（间隔越小，采样越多）                   
     * @param resetStatus   是否重置状态
     */
    public void processApiResources(String serviceEnName, LocalDate startDate, LocalDate endDate, Integer sampleCount,
            Integer hourSplit, Boolean resetStatus) {
        // 如果 serviceEnName 为空，遍历 basePathMap 的所有服务
        if (cn.hutool.core.util.StrUtil.isBlank(serviceEnName)) {
            log.info("serviceEnName 为空，将遍历 basePathMap 中的所有服务进行处理");
            Map<String, String> basePathMap = resourceSyncConfig.getBasePathMap();
            if (basePathMap != null && !basePathMap.isEmpty()) {
                for (String service : basePathMap.keySet()) {
                    log.info("开始处理服务: {}", service);
                    processApiResources(service, startDate, endDate, sampleCount, hourSplit, resetStatus);
                    log.info("服务 {} 处理完成", service);
                }
            } else {
                log.warn("basePathMap 为空，无法处理");
            }
            return;
        }

        // 如果 resetStatus=true，则重置指定 serviceEnName 的资源状态为 IMPORT_SUCCESS
        if (Boolean.TRUE.equals(resetStatus) && cn.hutool.core.util.StrUtil.isNotBlank(serviceEnName)) {
            log.info("开始重置服务 {} 的API资源状态为 IMPORT_SUCCESS", serviceEnName);
            List<AssetLineageResourceEntity> resourcesToReset = assetResourceMapper.selectApiResourcesByServiceEnName(serviceEnName);
            if (cn.hutool.core.collection.CollectionUtil.isNotEmpty(resourcesToReset)) {
                for (AssetLineageResourceEntity entity : resourcesToReset) {
                    entity.setStatus(ResourceStatusEnum.IMPORT_SUCCESS.getStatus());
                    entity.setUpdater(com.datafusion.manager.utils.HttpUtils.getCurrentUserName());
                    entity.setUpdateTime(new Date());
                    assetResourceMapper.updateById(entity);
                }
                log.info("已重置 {} 个API资源的状态", resourcesToReset.size());
            }
        }

        log.info("开始处理API资源链路分析任务，服务: {}，日期范围: {} ~ {}，小时切分: {}份，采样数: {}/段.",
                serviceEnName, startDate, endDate, hourSplit, sampleCount);

        if (sampleCount == null || sampleCount <= 0) {
            sampleCount = 5;
        }
        if (hourSplit == null || hourSplit <= 0) {
            hourSplit = 4;
        }

        if (startDate == null && endDate == null) {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            startDate = yesterday;
            endDate = yesterday;
            log.info("未指定日期范围，默认使用前一天: {} ~ {}.", startDate, endDate);
        } else if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
            log.info("未指定开始日期，默认使用一个月前: {}.", startDate);
        } else if (endDate == null) {
            endDate = startDate;
            log.info("未指定结束日期，默认使用开始日期: {}.", endDate);
        }

        String startDateStr = startDate.atStartOfDay().format(DB_DATE_FORMAT);
        String endDateStr = endDate.atTime(LocalTime.MAX).format(DB_DATE_FORMAT);

        List<AssetLineageResourceEntity> resources = assetResourceMapper.selectImportCompletedApiResources(
                serviceEnName, startDateStr, endDateStr);

        if (CollectionUtil.isEmpty(resources)) {
            log.info("没有待处理的API资源。");
            return;
        }

        log.info("查询到 {} 个待处理的API资源。", resources.size());

        Map<String, List<AssetLineageResourceEntity>> resourcesByService = resources.stream()
                .collect(Collectors.groupingBy(resource -> {
                    ResourceSnapshotBuilder.ApiResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(resource);
                    return snapshot != null ? snapshot.getServiceEnName() : "";
                }));

        log.info("按服务分组后共有 {} 个服务需要处理。", resourcesByService.size());

        for (Map.Entry<String, List<AssetLineageResourceEntity>> entry : resourcesByService.entrySet()) {
            String serviceName = entry.getKey();
            List<AssetLineageResourceEntity> serviceResources = entry.getValue();

            if (serviceName == null || serviceName.isEmpty()) {
                log.warn("跳过服务名为空的资源组。");
                continue;
            }

            processServiceResources(serviceName, serviceResources, startDate, endDate, sampleCount, hourSplit);
        }

        log.info("API资源链路分析任务处理完成。");
    }

    /**
     * 处理单个服务的所有资源.
     * @param serviceName    服务名
     * @param serviceResources 服务下的所有资源
     * @param startDate      开始日期
     * @param endDate        结束日期
     * @param sampleCount    每个耗时区间采样数量
     * @param hourSplit       间隔多长时间踩一次样本（间隔越小，采样越多）
     */
    @Transactional(rollbackFor = Throwable.class)
    public void processServiceResources(String serviceName, List<AssetLineageResourceEntity> serviceResources, LocalDate startDate,
                                        LocalDate endDate, int sampleCount, int hourSplit) {
        String lockKey = "api_link_update_lock:" + serviceName;
        RLock lock = redissonClient.getLock(lockKey);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        try {
            if (lock.tryLock(0, 10, TimeUnit.MINUTES)) {
                try {
                    List<Triple<AssetLineageResourceEntity, String, Pair<String, List<CallEdge>>>> results = executeApiLinkUpdate(
                            serviceName, serviceResources, startDate, endDate, sampleCount, hourSplit);

                    // --- 按ResourceID分组收集数据，WeLocation使用List收集，最终统一由mergeWeLocations合并 ---
                    Map<UUID, List<CallEdge>> resourceEdgesMap = new HashMap<>();
                    Map<UUID, List<ResourceSnapshotBuilder.WeLocation>> resourceWlMap = new HashMap<>();
                    Map<UUID, String> resourceTraceIdMap = new HashMap<>();

                    for (Triple<AssetLineageResourceEntity, String, Pair<String, List<CallEdge>>> triple : results) {
                        UUID resId = triple.getLeft().getId();
                        resourceEdgesMap.put(resId, mergeCallEdges(resourceEdgesMap.get(resId), triple.getRight().getRight()));
                        resourceTraceIdMap.put(resId, triple.getMiddle());

                        try {
                            List<ResourceSnapshotBuilder.WeLocation> list = mapper.readValue(triple.getRight().getLeft(),
                                    new com.fasterxml.jackson.core.type.TypeReference<List<ResourceSnapshotBuilder.WeLocation>>() {
                                    });
                            resourceWlMap.computeIfAbsent(resId, k -> new ArrayList<>()).addAll(list);
                        } catch (Exception e) {
                            log.warn("反序列化WeLocation失败, resId={}", resId, e);
                        }
                    }

                    List<AssetLineageResourceEntity> finished = new ArrayList<>();
                    for (AssetLineageResourceEntity resource : serviceResources) {
                        if (!resourceEdgesMap.containsKey(resource.getId())) {
                            continue;
                        }

                        ResourceSnapshotBuilder.ApiResourceResultSnapshot snapshot = ResourceSnapshotBuilder.builder(resource, true);

                        // --- 合并WeLocation：数据库原有 + 本次新解析，统一调用mergeWeLocations去重 ---
                        List<ResourceSnapshotBuilder.WeLocation> allWlToProcess = new ArrayList<>();
                        // A. 添加数据库中原有的
                        if (snapshot != null && snapshot.getCallChain() != null && snapshot.getCallChain().getWeLocationList() != null) {
                            allWlToProcess.addAll(snapshot.getCallChain().getWeLocationList());
                        }
                        // B. 添加本次新解析的
                        List<ResourceSnapshotBuilder.WeLocation> newWls = resourceWlMap.get(resource.getId());
                        if (newWls != null) {
                            allWlToProcess.addAll(newWls);
                        }

                        // C. 统一合并去重（基于 projectName + weLocation，tagSet取并集）
                        Set<ResourceSnapshotBuilder.WeLocation> finalWlSet = mergeWeLocations(allWlToProcess);

                        ResourceSnapshotBuilder.CallChain chain = new ResourceSnapshotBuilder.CallChain();
                        List<CallEdge> finalEdges = mergeCallEdges(
                                (snapshot != null && snapshot.getCallChain() != null) ? snapshot.getCallChain().getCallEdges() : null,
                                resourceEdgesMap.get(resource.getId()));
                        chain.setTraceId(resourceTraceIdMap.get(resource.getId()));
                        chain.setCallEdges(finalEdges);
                        chain.setWeLocationList(new ArrayList<>(finalWlSet));

                        if (snapshot == null) {
                            snapshot = new ResourceSnapshotBuilder.ApiResourceResultSnapshot();
                        }
                        snapshot.setCallChain(chain);
                        resource.setResultSnapshot(JacksonUtils.convertPojoToJsonNodeSafely(snapshot));
                        finished.add(resource);
                    }
                    updateResourceStatus(finished, ResourceStatusEnum.PARSE_SUCCESS.getStatus(), null);
                } finally {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("同步失败", e);
        }
    }

    /**
     * 按耗时区间分组采样.
     *
     * @param allTraces   所有 trace 列表（含耗时）
     * @param sampleCount 每组采样数
     * @return 采样后的 trace 列表
     */
    private List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> sampleByDuration(
            List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> allTraces, int sampleCount) {
        if (CollectionUtil.isEmpty(allTraces)) {
            return new ArrayList<>();
        }

        // 1. 按耗时从小到大排序
        List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> sortedTraces = allTraces.stream()
                .filter(trace -> trace.getDuration() != null)
                .sorted(Comparator.comparing(com.datafusion.manager.asset.dto.skywalking.BasicTraceDto::getDuration))
                .collect(Collectors.toList());

        if (sortedTraces.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 平均分成 5 组（耗时从短到长）
        int total = sortedTraces.size();
        int groupSize = total / 5;

        List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> group1 = new ArrayList<>(); // 第1段（最快）
        List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> group2 = new ArrayList<>(); // 第2段
        List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> group3 = new ArrayList<>(); // 第3段
        List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> group4 = new ArrayList<>(); // 第4段
        List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> group5 = new ArrayList<>(); // 第5段（最慢）

        for (int i = 0; i < total; i++) {
            if (i < groupSize) {
                group1.add(sortedTraces.get(i));
            } else if (i < groupSize * 2) {
                group2.add(sortedTraces.get(i));
            } else if (i < groupSize * 3) {
                group3.add(sortedTraces.get(i));
            } else if (i < groupSize * 4) {
                group4.add(sortedTraces.get(i));
            } else {
                group5.add(sortedTraces.get(i));
            }
        }

        // 3. 每组采样 sampleCount 条
        List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> result = new ArrayList<>();
        result.addAll(sampleFromGroup(group1, sampleCount));
        result.addAll(sampleFromGroup(group2, sampleCount));
        result.addAll(sampleFromGroup(group3, sampleCount));
        result.addAll(sampleFromGroup(group4, sampleCount));
        result.addAll(sampleFromGroup(group5, sampleCount));

        log.debug("按耗时排序后分组：段1={}条，段2={}条，段3={}条，段4={}条，段5={}条，采样{}条/段，共{}条.",
                group1.size(), group2.size(), group3.size(), group4.size(), group5.size(), sampleCount, result.size());

        return result;
    }

    /**
     * 从分组中采样.
     *
     * @param traces      分组内的 trace 列表
     * @param sampleCount 采样数量
     * @return 采样结果
     */
    private List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> sampleFromGroup(
            List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> traces, int sampleCount) {
        if (CollectionUtil.isEmpty(traces)) {
            return new ArrayList<>();
        }
        int size = Math.min(sampleCount, traces.size());
        // 取前 N 条（简单采样策略）
        return traces.subList(0, size);
    }

    /**
     * 执行Skywalking查询和调用链生成（方式2：先查询Endpoint，再通过endpointId查询链路）.
     */
    private List<Triple<AssetLineageResourceEntity, String, Pair<String, List<CallEdge>>>> executeApiLinkUpdate(
            String serviceName, List<AssetLineageResourceEntity> serviceResources, LocalDate startDate, LocalDate endDate,
            int sampleCount, int hourSplit) {
        List<Triple<AssetLineageResourceEntity, String, Pair<String, List<CallEdge>>>> resultList = new ArrayList<>();

        // 获取服务信息（使用整天范围查询）
        String from = startDate.atStartOfDay().format(SKYWALKING_TIME_FORMAT);
        String to = endDate.atTime(23, 59, 59).format(SKYWALKING_TIME_FORMAT);

        SkyWalkingServiceDto targetService = getServiceWithCache(serviceName, from, to);
        if (targetService == null) {
            log.warn("服务 {} 在Skywalking中未找到，跳过更新。", serviceName);
            return resultList;
        }

        // 按天循环，每天按小时切分查询
        int hoursPerSplit = 24 / hourSplit;
        LocalDate currentDate = startDate;
        int segmentIndex = 0;

        while (!currentDate.isAfter(endDate)) {
            for (int i = 0; i < hourSplit; i++) {
                int startHour = i * hoursPerSplit;
                int endHour = (i + 1) * hoursPerSplit - 1;
                if (i == hourSplit - 1) {
                    endHour = 23; // 最后一段到23点
                }

                // 如果是最后一天且超过endDate，则调整endHour
                if (currentDate.equals(endDate) && endHour > 23) {
                    // 这里简化处理，不做额外判断
                }

                String splitFrom = currentDate.atTime(startHour, 0).format(SKYWALKING_TIME_FORMAT);
                String splitTo = currentDate.atTime(endHour, 59, 59).format(SKYWALKING_TIME_FORMAT);

                // 跳过超过结束日期的时间段
                if (currentDate.isAfter(endDate)) {
                    break;
                }

                segmentIndex++;
                log.info("SkyWalking查询第{}个时间段: {} ~ {}", segmentIndex, splitFrom, splitTo);

                // 查询该时间段的trace
                List<Triple<AssetLineageResourceEntity, String, Pair<String, List<CallEdge>>>> segmentResults = queryTracesForTimeSegment(serviceName,
                        targetService, serviceResources, splitFrom, splitTo, sampleCount);
                resultList.addAll(segmentResults);
            }
            currentDate = currentDate.plusDays(1);
        }

        log.info("SkyWalking查询完成，共查询{}个时间段", segmentIndex);
        return resultList;
    }

    /**
     * 查询指定时间段的trace.
     */
    private List<Triple<AssetLineageResourceEntity, String, Pair<String, List<CallEdge>>>> queryTracesForTimeSegment(
            String serviceName,
            SkyWalkingServiceDto targetService,
            List<AssetLineageResourceEntity> serviceResources,
            String from, String to, int sampleCount) {
        log.info("在Skywalking中找到服务 {}，ID: {}.", serviceName, targetService.getId());

        Map<String, AssetLineageResourceEntity> endpointToResourceMap = new HashMap<>();
        Map<String, String> normalizedEndpointMap = new HashMap<>();
        for (AssetLineageResourceEntity resource : serviceResources) {
            ResourceSnapshotBuilder.ApiResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(resource);
            if (snapshot != null) {
                // 注意下面取的snapshot.getEndPoint()
                String endpoint = snapshot.getRequestType() + SystemConstant.COLON + snapshot.getRequestUrl();
                endpointToResourceMap.put(endpoint, resource);
                normalizedEndpointMap.put(endpoint, normalizeEndpoint(endpoint));
            }
        }
        log.info("目标接口数量: {}.", endpointToResourceMap.size());

        List<EndpointDto> endpoints = graphqlService.findEndpoints(targetService.getId(), null, 1000);
        log.info("服务 {} 在Skywalking中有 {} 个Endpoint.", serviceName, endpoints.size());

        Set<AssetLineageResourceEntity> matchedResources = new HashSet<>();
        List<Triple<AssetLineageResourceEntity, String, Pair<String, List<CallEdge>>>> resultList = new ArrayList<>();
        for (EndpointDto endpoint : endpoints) {
            String endpointName = endpoint.getName();
            if ((endpointName.startsWith("POST:") || endpointName.startsWith("GET:"))) {
                String requestUrl = parseRequestUrl(endpointName);
                String requestType = parseRequestType(endpointName);
                // 如果获取的并非basePath开头，则拼接进去
                if (!requestUrl.startsWith(resourceSyncConfig.getBasePathMap().get(serviceName))) {
                    endpointName = requestType + SystemConstant.COLON + resourceSyncConfig.getBasePathMap().get(serviceName) + requestUrl;
                }

                if (isTargetEndpoint(endpointName, endpointToResourceMap.keySet())) {

                    log.info("开始分析接口: {}.", endpointName);

                    // 获取所有 trace（带耗时），按耗时分组采样
                    List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> allTraces = graphqlService.queryBasicTraces(
                            targetService.getId(),
                            endpoint.getId(),
                            from,
                            to,
                            "MINUTE",
                            "SUCCESS", "BY_START_TIME",
                            1, 200); // 获取最多 200 条

                    if (CollectionUtil.isEmpty(allTraces)) {
                        log.info("接口 {} 没有链路数据。", endpointName);
                        continue;
                    }

                    // 按耗时分组采样
                    List<com.datafusion.manager.asset.dto.skywalking.BasicTraceDto> sampledTraces = sampleByDuration(allTraces, sampleCount);
                    log.info("接口 {} 采样 {} 条 trace（耗时分组后）。", endpointName, sampledTraces.size());

                    // 遍历采样的 trace，获取完整链路
                    List<CallEdge> callEdges = new ArrayList<>();
                    List<String> traceIds = new ArrayList<>();
                    // 处理 weLocation 列表（解析所有采样 trace），使用List收集避免HashSet去重丢失tagSet
                    List<ResourceSnapshotBuilder.WeLocation> allWeLocations = new ArrayList<>();

                    for (com.datafusion.manager.asset.dto.skywalking.BasicTraceDto basicTrace : sampledTraces) {
                        if (CollectionUtil.isEmpty(basicTrace.getTraceIds())) {
                            continue;
                        }
                        String traceId = basicTrace.getTraceIds().get(0);
                        traceIds.add(traceId);

                        List<SpanDto> spans = graphqlService.queryTrace(traceId);

                        if (CollectionUtil.isNotEmpty(spans)) {
                            Set<CallEdge> edges = traceGraphBuilder.buildCallGraph(spans);
                            callEdges = mergeCallEdges(callEdges, edges);
                            Set<ResourceSnapshotBuilder.WeLocation> wlList = traceGraphBuilder.parseWeLocations(spans);
                            allWeLocations.addAll(wlList);
                        }
                    }

                    AssetLineageResourceEntity matchedResource = findMatchedResource(
                            endpointName, endpointToResourceMap, normalizedEndpointMap);

                    if (matchedResource != null && !matchedResources.contains(matchedResource)) {
                        List<CallEdge> edges = new ArrayList<>(callEdges);
                        // 取最后一个 traceId
                        String lastTraceId = traceIds.isEmpty() ? "" : traceIds.get(traceIds.size() - 1);
                        // location 字段改为传递 weLocationList 的 JSON 字符串
                        String weLocationListJson = JacksonUtils.tryObj2Str(allWeLocations);
                        resultList.add(Triple.of(matchedResource, lastTraceId, Pair.of(weLocationListJson, edges)));
                        matchedResources.add(matchedResource);

                        log.info("接口 {} 关联到资源 {}，新增 {} 条调用关系。",
                                endpointName, matchedResource.getId(), edges.size());

                        if (matchedResources.size() >= serviceResources.size()) {
                            log.info("所有资源都已匹配到链路，停止查询。");
                            return resultList;
                        }
                    }
                }
            }
        }

        log.info("服务 {} 的链路分析完成，共获取 {} 个资源的调用关系。", serviceName, resultList.size());
        return resultList;
    }

    /**
     * 解析接口请求路径.
     */
    private String parseRequestUrl(String endPoint) {
        return endPoint.substring(endPoint.indexOf(":") + 1);
    }

    /**
     * 解析接口请求方式.
     */
    private String parseRequestType(String endPoint) {
        return endPoint.substring(0, endPoint.indexOf(":"));
    }

    private String getLocation(List<SpanDto> spans) {
        SpanDto spanDto = spans.get(0);
        Optional<TagDto> tagDto = spanDto.getTags().stream()
                .filter(tag -> "http.headers".equalsIgnoreCase(tag.getKey()))
                .findFirst();
        String location = Strings.EMPTY;

        if (tagDto.isPresent()) {
            String headersValue = tagDto.get().getValue();
            // 提取 we-location 的值
            String[] lines = headersValue.split("\n");
            for (String line : lines) {
                if (line.startsWith("we-location=[")) {
                    // 去除前缀和后缀，获取完整的 URL
                    String url = line.substring("we-location=[".length(), line.length() - 1);
                    try {
                        // 解码 URL（处理 % 编码）
                        String decodedUrl = java.net.URLDecoder.decode(url, "UTF-8");
                        // 提取 /#/ 后面的内容
                        int fragmentIndex = decodedUrl.indexOf("/#/");
                        if (fragmentIndex != -1 && fragmentIndex < decodedUrl.length() - 3) {
                            location = decodedUrl.substring(fragmentIndex + 3);
                        }
                    } catch (Exception e) {
                        log.warn("解码 URL 时发生异常: {}", url, e);
                    }
                    break;
                }
            }
        }

        return location.trim();
    }

    /**
     * 合并 WeLocation 集合，对相同 projectName+weLocation 的 tagSet 取并集.
     */
    private Set<ResourceSnapshotBuilder.WeLocation> mergeWeLocations(Collection<ResourceSnapshotBuilder.WeLocation> locations) {
        // Key: projectName + "|" + weLocation
        Map<String, ResourceSnapshotBuilder.WeLocation> map = new HashMap<>();

        for (ResourceSnapshotBuilder.WeLocation wl : locations) {
            if (wl == null || StringUtils.isAnyBlank(wl.getProjectName(), wl.getWeLocation())) {
                continue;
            }

            String key = (wl.getProjectName() + "|" + wl.getWeLocation()).trim();

            if (map.containsKey(key)) {
                ResourceSnapshotBuilder.WeLocation existing = map.get(key);
                if (CollectionUtil.isNotEmpty(wl.getTagSet())) {
                    if (existing.getTagSet() == null) {
                        existing.setTagSet(new HashSet<>());
                    }
                    existing.getTagSet().addAll(wl.getTagSet());
                }
            } else {
                ResourceSnapshotBuilder.WeLocation newWl = new ResourceSnapshotBuilder.WeLocation();
                newWl.setProjectName(wl.getProjectName());
                newWl.setWeLocation(wl.getWeLocation());
                newWl.setTagSet(wl.getTagSet() != null ? new HashSet<>(wl.getTagSet()) : new HashSet<>());
                map.put(key, newWl);
            }
        }
        return new HashSet<>(map.values());
    }

    /**
     * 从缓存获取服务信息，缓存一周.
     *
     * @param serviceName 服务名称
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @return SkyWalkingServiceDto
     */
    private SkyWalkingServiceDto getServiceWithCache(String serviceName, String startTime, String endTime) {
        String cacheKey = SERVICE_CACHE_PREFIX + serviceName;

        Object cachedService = redissonClient.getBucket(cacheKey).get();
        if (cachedService instanceof SkyWalkingServiceDto) {
            log.debug("从缓存获取服务 {} 的信息。", serviceName);
            return (SkyWalkingServiceDto) cachedService;
        }

        log.info("缓存未命中，查询Skywalking获取服务 {} 的信息。", serviceName);
        List<SkyWalkingServiceDto> allServices = graphqlService.getAllServices(startTime, endTime, "MINUTE");
        SkyWalkingServiceDto targetService = allServices.stream()
                .filter(s -> serviceName.equals(s.getName()))
                .findFirst()
                .orElse(null);

        if (targetService != null) {
            redissonClient.getBucket(cacheKey).set(targetService,
                    SERVICE_CACHE_TTL_DAYS, TimeUnit.DAYS);
            log.info("服务 {} 的信息已缓存，过期时间：{} 天。", serviceName, SERVICE_CACHE_TTL_DAYS);
        }

        return targetService;
    }

    /**
     * 查找匹配的资源.
     *
     * @param calleeEndpoint        目标端点
     * @param endpointToResourceMap 端点资源映射
     * @param normalizedEndpointMap 正则化后的端点映射
     * @return 匹配的资源
     */
    private AssetLineageResourceEntity findMatchedResource(String calleeEndpoint,
            Map<String, AssetLineageResourceEntity> endpointToResourceMap,
            Map<String, String> normalizedEndpointMap) {
        AssetLineageResourceEntity resource = endpointToResourceMap.get(calleeEndpoint);
        if (resource != null) {
            return resource;
        }

        String normalizedCallee = normalizeEndpoint(calleeEndpoint);
        for (Map.Entry<String, String> entry : normalizedEndpointMap.entrySet()) {
            if (normalizedCallee.equals(entry.getValue())) {
                return endpointToResourceMap.get(entry.getKey());
            }
        }

        return null;
    }

    /**
     * 匹配端点.
     *
     * @param endpointName 端点名称
     * @param target       目标端点
     * @return 是否匹配
     */
    private boolean matchEndpoints(String endpointName, String target) {
        return endpointName.equals(target);
    }

    /**
     * 正则化端点.
     *
     * @param endpoint 端点
     * @return 正则化后的端点
     */
    private String normalizeEndpoint(String endpoint) {
        int queryIndex = endpoint.indexOf('?');
        if (queryIndex > 0) {
            endpoint = endpoint.substring(0, queryIndex);
        }
        return endpoint;
    }

    /**
     * 判断目标端点是否是目标端点.
     *
     * @param endpointName    端点名称
     * @param targetEndpoints 目标端点列表
     * @return 是否是目标端点
     */
    private boolean isTargetEndpoint(String endpointName, Set<String> targetEndpoints) {
        if (targetEndpoints.contains(endpointName)) {
            return true;
        }

        String normalizedEndpoint = normalizeEndpoint(endpointName);
        for (String target : targetEndpoints) {
            if (matchEndpoints(normalizedEndpoint, target)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 更新资源状态.
     *
     * @param resources    资源列表
     * @param status       状态
     * @param result       结果
     */
    private void updateResourceStatus(List<AssetLineageResourceEntity> resources, int status, JsonNode result) {
        if (CollectionUtil.isEmpty(resources)) {
            return;
        }

        for (AssetLineageResourceEntity entity : resources) {
            entity.setStatus(status);
            entity.setResult(result);
            entity.setUpdater(HttpUtils.getCurrentUserName());
            entity.setUpdateTime(new Date());
            assetResourceMapper.updateById(entity);
        }
        log.info("批量更新资源状态为 {}，数量: {}.", status, resources.size());
    }

    /**
     * 深度合并CallEdge，相同uniqueId的tagSet取并集，duration取最大值.
     */
    private List<CallEdge> mergeCallEdges(Collection<CallEdge> base, Collection<CallEdge> addition) {
        Map<String, CallEdge> map = new HashMap<>();
        if (CollectionUtil.isNotEmpty(base)) {
            for (CallEdge e : base) {
                if (e != null) {
                    CallEdge copy = new CallEdge(e.getCallerService(), e.getCallerEndpoint(),
                            e.getCalleeService(), e.getCalleeEndpoint(), e.getCallType(), e.getDuration(),
                            e.getTagSet() != null ? new HashSet<>(e.getTagSet()) : null);
                    map.put(copy.getUniqueId(), copy);
                }
            }
        }
        if (CollectionUtil.isNotEmpty(addition)) {
            for (CallEdge ne : addition) {
                if (ne == null) {
                    continue;
                }
                if (map.containsKey(ne.getUniqueId())) {
                    CallEdge existing = map.get(ne.getUniqueId());
                    if (ne.getTagSet() != null) {
                        if (existing.getTagSet() == null) {
                            existing.setTagSet(new HashSet<>());
                        }
                        existing.getTagSet().addAll(ne.getTagSet());
                    }
                    existing.setDuration(Math.max(existing.getDuration(), ne.getDuration()));
                } else {
                    CallEdge copy = new CallEdge(ne.getCallerService(), ne.getCallerEndpoint(),
                            ne.getCalleeService(), ne.getCalleeEndpoint(), ne.getCallType(), ne.getDuration(),
                            ne.getTagSet() != null ? new HashSet<>(ne.getTagSet()) : null);
                    map.put(copy.getUniqueId(), copy);
                }
            }
        }
        return new ArrayList<>(map.values());
    }
}
