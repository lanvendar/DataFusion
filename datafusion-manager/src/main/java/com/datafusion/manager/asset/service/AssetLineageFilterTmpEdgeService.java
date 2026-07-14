package com.datafusion.manager.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.asset.dao.AssetLineageEdgeMapper;
import com.datafusion.manager.asset.dto.AssetNodeAttributesDto;
import com.datafusion.manager.asset.dto.AttributeEdgeVo;
import com.datafusion.manager.asset.dto.EdgeNodeRequestVo;
import com.datafusion.manager.asset.dto.EdgeNodeVoV2;
import com.datafusion.manager.asset.dto.EntityEdgeVo;
import com.datafusion.manager.asset.dto.LineEdgeNodeVoV3;
import com.datafusion.manager.asset.dto.LineageEdgeDto;
import com.datafusion.manager.asset.enums.NodeSubTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于现有 linkTable 的返回结构，新增 tmp_ 表递归扩展查询.
 * - 每次只查询 1-hop（上游一层或下游一层）
 * - {@code isLeafNode=false}：以表 URN 为中心，扩展<strong>表级</strong>边；tmp_ 判断在表名，继续扩展时队列入队<strong>表 URN</strong>
 * - {@code isLeafNode=true}：以列 URN 为中心，扩展<strong>字段级</strong>边；tmp_ 判断在该列所属表名，继续扩展时队列入队<strong>邻接列 URN</strong>
 * - {@code isUp=true} 只扩展上游；{@code isUp=false} 只扩展下游；{@code isUp=null} 上下游都扩展（与 linkTable 一致）
 * - 扩展完成后会做<strong>压缩</strong>：去掉中间 tmp_ 节点，将 {@code 源 -> tmp* -> 目标} 合并为 {@code 源 -> 目标}，与接口「去除 tmp 表」展示一致
 *
 * <p>注意：该类为新增实现，不修改原有 AssetLineageEdgeService.
 *
 * @author GPT
 * @version 1.0.0 , 2026/04/16
 * @since 2026/04/09
 */
@Service
@RequiredArgsConstructor
public class AssetLineageFilterTmpEdgeService {

    /** 血缘边数据访问对象. */
    private final AssetLineageEdgeMapper edgeMapper;

    /** 血缘节点服务. */
    private final AssetLineageNodeService nodeService;

    /**
     * tmp_ 递归扩展版表级血缘（V3）.
     *
     * @param req 查询请求（nodeUrn 必填；isLeafNode=true 时 depth 只能为 1）
     * @return V3 血缘结果
     */
    public LineEdgeNodeVoV3 linkTableTmpRecursive(EdgeNodeRequestVo req) {
        validateReq(req);

        boolean isLeafNode = Boolean.TRUE.equals(req.getIsLeafNode());
        String startCenterUrn = req.getNodeUrn();

        List<LineageEdgeDto> edgeNodes = expandTmpBiDirectional(startCenterUrn, isLeafNode, req.getIsUp());
        if (edgeNodes.isEmpty()) {
            return emptyV3(startCenterUrn, isLeafNode);
        }
        edgeNodes = collapseTmpIntermediates(edgeNodes);
        edgeNodes = removeEdgesTouchingTmp(edgeNodes);
        edgeNodes = collapseTmpIntermediates(edgeNodes);
        edgeNodes = removeEdgesTouchingTmp(edgeNodes);
        if (edgeNodes.isEmpty()) {
            return emptyV3(startCenterUrn, isLeafNode);
        }
        return stripTmpFromLineageVo(buildV3FromEdges(edgeNodes, isLeafNode));
    }

    private void validateReq(EdgeNodeRequestVo req) {
        if (req == null || StringUtils.isBlank(req.getNodeUrn())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "查询节点urn不能为空");
        }
        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetLineageNodeEntity::getNodeUrn, req.getNodeUrn());
        AssetLineageNodeEntity nodeEntity = nodeService.getOne(wrapper);
        if (nodeEntity == null || nodeEntity.getNodeSubType() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "nodeUrn不存在,或者为叶子节点");
        }
        if (Boolean.TRUE.equals(req.getIsLeafNode())) {
            // 只允许 -1 到 1（原逻辑：字段级仅允许 depth=1）
            if (req.getDepth() != null && req.getDepth() != 1) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "下层节点展示时,只允许为1");
            }
        }
    }

    /**
     * 上下游扩展：每次只取 1-hop，仅当邻接点所在表为 tmp_ 时继续扩展.
     * <ul>
     *   <li>{@code isUp == true}：只扩展上游（depth=-1）；</li>
     *   <li>{@code isUp == false}：只扩展下游（depth=1）；</li>
     *   <li>{@code isUp == null}：上下游都扩展（与 linkTable 默认一致）。</li>
     * </ul>
     * 说明：SQL {@code link} 仍一次返回上下游，此处按 depth 过滤并只驱动对应队列。
     */
    private List<LineageEdgeDto> expandTmpBiDirectional(String startCenterUrn, boolean isLeafNode, Boolean isUp) {
        final int maxRounds = 1000;
        final int maxStartNodes = 5000;
        final int maxEdges = 50000;

        boolean expandUp = isUp == null || Boolean.TRUE.equals(isUp);
        boolean expandDown = isUp == null || Boolean.FALSE.equals(isUp);

        Set<String> visitedUpStart = new HashSet<>();
        Set<String> visitedDownStart = new HashSet<>();
        Deque<String> upQ = new ArrayDeque<>();
        Deque<String> downQ = new ArrayDeque<>();

        Set<String> visitedEdge = new HashSet<>();
        List<LineageEdgeDto> all = new ArrayList<>();

        if (expandUp) {
            visitedUpStart.add(startCenterUrn);
            upQ.add(startCenterUrn);
        }
        if (expandDown) {
            visitedDownStart.add(startCenterUrn);
            downQ.add(startCenterUrn);
        }

        int rounds = 0;
        while (((expandUp && !upQ.isEmpty()) || (expandDown && !downQ.isEmpty())) && rounds++ < maxRounds) {
            if (visitedUpStart.size() + visitedDownStart.size() > maxStartNodes) {
                break;
            }
            if (all.size() > maxEdges) {
                break;
            }

            if (expandUp && !upQ.isEmpty()) {
                String center = upQ.poll();
                List<LineageEdgeDto> oneHopUp = oneHop(center, isLeafNode, true).stream()
                        .filter(e -> e.getDepth() == -1)
                        .collect(Collectors.toList());
                for (LineageEdgeDto e : oneHopUp) {
                    addEdgeDedup(all, visitedEdge, e);

                    // depth=-1：sourceUrn 为上游一跳（表模式为表 URN，字段模式为列 URN）
                    String upstreamNeighbor = e.getSourceUrn();
                    if (isTmpTableUrn(upstreamNeighbor) && visitedUpStart.add(upstreamNeighbor)) {
                        upQ.add(upstreamNeighbor);
                    }
                }
            }

            if (expandDown && !downQ.isEmpty()) {
                String center = downQ.poll();
                List<LineageEdgeDto> oneHopDown = oneHop(center, isLeafNode, false).stream()
                        .filter(e -> e.getDepth() == 1)
                        .collect(Collectors.toList());
                for (LineageEdgeDto e : oneHopDown) {
                    addEdgeDedup(all, visitedEdge, e);

                    // depth=1：targetUrn 为下游一跳（表模式为表 URN，字段模式为列 URN）
                    String downstreamNeighbor = e.getTargetUrn();
                    if (isTmpTableUrn(downstreamNeighbor) && visitedDownStart.add(downstreamNeighbor)) {
                        downQ.add(downstreamNeighbor);
                    }
                }
            }
        }

        return all;
    }

    /**
     * 1-hop 查询：复用 edgeMapper.link(req)，通过 depth=1 限制为一层.
     *
     * @param centerNodeUrn 中心节点 URN（表模式为表 URN；字段模式为列 URN）
     * @param isLeafNode    是否叶子节点模式（字段/指标）
     * @param isUp          true=仅上游；false=仅下游
     */
    private List<LineageEdgeDto> oneHop(String centerNodeUrn, boolean isLeafNode, boolean isUp) {
        EdgeNodeRequestVo req = new EdgeNodeRequestVo();
        req.setNodeUrn(centerNodeUrn);
        req.setDepth(1);
        req.setIsLeafNode(isLeafNode);
        req.setIsUp(isUp);
        return edgeMapper.link(req);
    }

    private void addEdgeDedup(List<LineageEdgeDto> all, Set<String> visitedEdge, LineageEdgeDto e) {
        if (e == null || StringUtils.isBlank(e.getSourceUrn()) || StringUtils.isBlank(e.getTargetUrn())) {
            return;
        }
        String key = e.getSourceUrn() + "->" + e.getTargetUrn();
        if (visitedEdge.add(key)) {
            all.add(e);
        }
    }

    /**
     * 与 {@link AssetLineageEdgeService} 中列级 URN 约定一致：按 {@code :} 分段后段数 &gt; 5 视为列 URN（含字段段）.
     */
    private boolean isColumnLikeUrnStructurally(String urn) {
        if (urn == null || !urn.contains(SystemConstant.COLON)) {
            return false;
        }
        return urn.split(SystemConstant.COLON, -1).length > 5;
    }

    /**
     * 判断邻接点所在表是否为 tmp_：先归一到表级 URN（仅列 URN 去最后一段），再判断表名前缀.
     */
    private boolean isTmpTableUrn(String urn) {
        if (StringUtils.isBlank(urn)) {
            return false;
        }
        String tableLevelUrn = getTableUrn(urn);
        if (StringUtils.isBlank(tableLevelUrn) || !tableLevelUrn.contains(SystemConstant.COLON)) {
            return false;
        }
        String tableName = tableLevelUrn.substring(tableLevelUrn.lastIndexOf(SystemConstant.COLON) + 1);
        return tableName.startsWith("tmp_") || tableName.startsWith("tmp.");
    }

    /**
     * 从 URN 得到表级 URN.
     *
     * <p>仅当结构上是列 URN（段数 &gt; 5）时去掉最后一列段；
     * 避免 {@code isLeafNode=true} 时误把 5 段表 URN 当成「表:列」而截断表名。
     */
    private String getTableUrn(String urn) {
        if (urn == null) {
            return null;
        }
        if (isColumnLikeUrnStructurally(urn)) {
            return urn.substring(0, urn.lastIndexOf(SystemConstant.COLON));
        }
        return urn;
    }

    /**
     * 判断 URN 是否落在「临时表」上（与请求 isLeafNode 无关）.
     * <ul>
     *   <li>列 URN（段数 &gt; 5）：用去掉最后一列后的表 URN 的最后一段判断表名；</li>
     *   <li>表 URN：同时用「最后一段」判断（避免仅按段数误判时漏识别 tmp 表）。</li>
     * </ul>
     */
    private boolean isTmpLineageNode(String urn) {
        if (StringUtils.isBlank(urn) || !urn.contains(SystemConstant.COLON)) {
            return false;
        }
        String[] parts = urn.split(SystemConstant.COLON, -1);
        if (parts.length > 5) {
            String tableUrn = urn.substring(0, urn.lastIndexOf(SystemConstant.COLON));
            int li = tableUrn.lastIndexOf(SystemConstant.COLON);
            if (li >= 0) {
                String tableName = tableUrn.substring(li + 1);
                if (tableName.startsWith("tmp_") || tableName.startsWith("tmp.")) {
                    return true;
                }
            }
        }
        String last = parts[parts.length - 1];
        return last.startsWith("tmp_") || last.startsWith("tmp.");
    }

    /**
     * 去掉图中的 tmp_ 中间节点：对任意 tmp 节点 T，将所有 s-&gt;T 与 T-&gt;t 合并为 s-&gt;t，并删除与 T 相连的边.
     * 支持 tmp 链（T1-&gt;T2-&gt;…）多次迭代消去。
     */

    private List<LineageEdgeDto> collapseTmpIntermediates(List<LineageEdgeDto> edges) {
        Map<String, Set<String>> out = new HashMap<>();
        for (LineageEdgeDto e : edges) {
            String s = e.getSourceUrn();
            String t = e.getTargetUrn();
            if (StringUtils.isBlank(s) || StringUtils.isBlank(t) || s.equals(t)) {
                continue;
            }
            out.computeIfAbsent(s, k -> new HashSet<>()).add(t);
        }

        int iterations = 0;
        final int maxIterations = 10000;
        while (iterations++ < maxIterations) {
            String tmpNode = findAnyTmpEndpoint(out);
            if (tmpNode == null) {
                break;
            }
            Set<String> sources = new HashSet<>();
            for (Map.Entry<String, Set<String>> e : new ArrayList<>(out.entrySet())) {
                Set<String> ts = e.getValue();
                if (ts != null && ts.contains(tmpNode)) {
                    sources.add(e.getKey());
                }
            }
            Set<String> targets = new HashSet<>(out.getOrDefault(tmpNode, Collections.emptySet()));

            for (String s : sources) {
                Set<String> ts = out.get(s);
                if (ts != null) {
                    ts.remove(tmpNode);
                    if (ts.isEmpty()) {
                        out.remove(s);
                    }
                }
            }
            out.remove(tmpNode);

            for (String s : sources) {
                for (String t : targets) {
                    if (s.equals(t)) {
                        continue;
                    }
                    out.computeIfAbsent(s, k -> new HashSet<>()).add(t);
                }
            }
        }

        List<LineageEdgeDto> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : out.entrySet()) {
            String s = e.getKey();
            for (String t : e.getValue()) {
                if (s.equals(t)) {
                    continue;
                }
                if (isTmpLineageNode(s) || isTmpLineageNode(t)) {
                    continue;
                }
                result.add(new LineageEdgeDto().setSourceUrn(s).setTargetUrn(t).setDepth(0));
            }
        }
        return result;
    }

    /**
     * 强制删除仍与 tmp 相连的边（折叠未识别或图中有多种 URN 形态时的兜底）.
     */
    private List<LineageEdgeDto> removeEdgesTouchingTmp(List<LineageEdgeDto> edges) {
        if (edges == null || edges.isEmpty()) {
            return edges;
        }
        return edges.stream()
                .filter(e -> e != null && StringUtils.isNotBlank(e.getSourceUrn()) && StringUtils.isNotBlank(e.getTargetUrn()))
                .filter(e -> !isTmpLineageNode(e.getSourceUrn()) && !isTmpLineageNode(e.getTargetUrn()))
                .collect(Collectors.toList());
    }

    private String findAnyTmpEndpoint(Map<String, Set<String>> out) {
        for (Map.Entry<String, Set<String>> e : out.entrySet()) {
            if (isTmpLineageNode(e.getKey())) {
                return e.getKey();
            }
            for (String t : e.getValue()) {
                if (isTmpLineageNode(t)) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * 从 V3 结果中剔除仍残留的 tmp 实体边、属性边、节点与 attributes.
     */
    private LineEdgeNodeVoV3 stripTmpFromLineageVo(LineEdgeNodeVoV3 vo) {
        if (vo == null) {
            return null;
        }
        if (vo.getEntityEdgeVos() != null) {
            Set<String> seenEntity = new HashSet<>();
            vo.setEntityEdgeVos(vo.getEntityEdgeVos().stream()
                    .filter(e -> e != null && StringUtils.isNotBlank(e.getSource()) && StringUtils.isNotBlank(e.getTarget()))
                    .filter(e -> !isTmpLineageNode(e.getSource()) && !isTmpLineageNode(e.getTarget()))
                    .filter(e -> seenEntity.add(e.getSource() + "->" + e.getTarget()))
                    .collect(Collectors.toList()));
        }
        if (vo.getAttributeEdgeVos() != null) {
            vo.setAttributeEdgeVos(vo.getAttributeEdgeVos().stream()
                    .filter(e -> e != null)
                    .filter(e -> StringUtils.isNotBlank(e.getSourceHandle()) && StringUtils.isNotBlank(e.getTargetHandle()))
                    .filter(e -> !isTmpLineageNode(e.getSource()) && !isTmpLineageNode(e.getTarget()))
                    .filter(e -> !isTmpLineageNode(e.getSourceHandle()) && !isTmpLineageNode(e.getTargetHandle()))
                    .collect(Collectors.toList()));
        }
        if (vo.getNodeVos() != null) {
            List<EdgeNodeVoV2> kept = new ArrayList<>();
            for (EdgeNodeVoV2 n : vo.getNodeVos()) {
                if (n == null || StringUtils.isBlank(n.getNodeUrn())) {
                    continue;
                }
                if (isTmpLineageNode(n.getNodeUrn())) {
                    continue;
                }
                if (n.getAttributes() != null && !n.getAttributes().isEmpty()) {
                    Set<AssetNodeAttributesDto> attrs = n.getAttributes().stream()
                            .filter(a -> a != null && StringUtils.isNotBlank(a.getAttributeUrn()))
                            .filter(a -> !isTmpLineageNode(a.getAttributeUrn()))
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    n.setAttributes(attrs.isEmpty() ? null : attrs);
                }
                kept.add(n);
            }
            vo.setNodeVos(kept);
        }
        return vo;
    }

    // ===================== V3 组装（对齐 linkTable） =====================

    private LineEdgeNodeVoV3 buildV3FromEdges(List<LineageEdgeDto> edgeNodes, boolean isLeafNode) {
        LineEdgeNodeVoV3 result = new LineEdgeNodeVoV3();

        Map<Integer, List<LineageEdgeDto>> edgeMaps = edgeNodes.stream()
                .collect(Collectors.groupingBy(LineageEdgeDto::getDepth));

        List<AssetLineageNodeEntity> nodeList = getResourceNode(edgeMaps);
        Map<String, AssetLineageNodeEntity> nodeMap = nodeList.stream()
                .collect(Collectors.toMap(AssetLineageNodeEntity::getNodeUrn, v -> v, (a, b) -> a));

        List<EdgeNodeVoV2> allNodeVos = new ArrayList<>();
        List<EntityEdgeVo> allEntityEdgeVos = new ArrayList<>();
        List<AttributeEdgeVo> allAttributeEdgeVos = new ArrayList<>();
        Map<String, EdgeNodeVoV2> nodeVoMap = new HashMap<>();

        for (Map.Entry<Integer, List<LineageEdgeDto>> entry : edgeMaps.entrySet()) {
            List<LineageEdgeDto> edgeDtos = entry.getValue();
            for (LineageEdgeDto edgeDto : edgeDtos) {
                String sourceUrn = edgeDto.getSourceUrn();
                String targetUrn = edgeDto.getTargetUrn();

                boolean isSourceAttributeEdge = isAttributeEdge(sourceUrn, nodeMap);
                boolean isTargetAttributeEdge = isAttributeEdge(targetUrn, nodeMap);

                if (!isLeafNode) {
                    ensureNode(sourceUrn, allNodeVos, nodeVoMap);
                    ensureNode(targetUrn, allNodeVos, nodeVoMap);

                    if (isSourceAttributeEdge || isTargetAttributeEdge) {
                        AttributeEdgeVo attrEdge = new AttributeEdgeVo();
                        attrEdge.setId(sourceUrn + "->" + targetUrn);
                        attrEdge.setSource(getTableUrn(sourceUrn));
                        attrEdge.setTarget(getTableUrn(targetUrn));
                        attrEdge.setSourceHandle(sourceUrn);
                        attrEdge.setTargetHandle(targetUrn);
                        allAttributeEdgeVos.add(attrEdge);
                    } else {
                        EntityEdgeVo entityEdge = new EntityEdgeVo();
                        entityEdge.setId(sourceUrn + "->" + targetUrn);
                        entityEdge.setSource(sourceUrn);
                        entityEdge.setTarget(targetUrn);
                        allEntityEdgeVos.add(entityEdge);
                    }
                } else {
                    String parentSourceUrn;
                    String parentTargetUrn;
                    if (isSourceAttributeEdge || isTargetAttributeEdge) {
                        parentSourceUrn = getTableUrn(sourceUrn);
                        parentTargetUrn = getTableUrn(targetUrn);
                    } else {
                        parentSourceUrn = sourceUrn;
                        parentTargetUrn = targetUrn;
                    }

                    if (!sourceUrn.equals(parentSourceUrn)) {
                        processParentChildNodeNew(sourceUrn, parentSourceUrn, allNodeVos, nodeVoMap);
                    }
                    if (!targetUrn.equals(parentTargetUrn)) {
                        processParentChildNodeNew(targetUrn, parentTargetUrn, allNodeVos, nodeVoMap);
                    }

                    if (isSourceAttributeEdge || isTargetAttributeEdge) {
                        AttributeEdgeVo attrEdge = new AttributeEdgeVo();
                        attrEdge.setId(sourceUrn + "->" + targetUrn);
                        attrEdge.setSource(parentSourceUrn);
                        attrEdge.setTarget(parentTargetUrn);
                        attrEdge.setSourceHandle(sourceUrn);
                        attrEdge.setTargetHandle(targetUrn);
                        allAttributeEdgeVos.add(attrEdge);

                        // 当 target 是 metric / column 时，补一条实体边（对齐 linkTable 行为）
                        if (isTargetAttributeEdge) {
                            EntityEdgeVo entityEdge = new EntityEdgeVo();
                            entityEdge.setId(sourceUrn + "->" + targetUrn);
                            entityEdge.setSource(parentSourceUrn);
                            entityEdge.setTarget(parentTargetUrn);
                            allEntityEdgeVos.add(entityEdge);
                        }
                    } else {
                        ensureNode(sourceUrn, allNodeVos, nodeVoMap);
                        ensureNode(targetUrn, allNodeVos, nodeVoMap);

                        EntityEdgeVo entityEdge = new EntityEdgeVo();
                        entityEdge.setId(sourceUrn + "->" + targetUrn);
                        entityEdge.setSource(parentSourceUrn);
                        entityEdge.setTarget(parentTargetUrn);
                        allEntityEdgeVos.add(entityEdge);
                    }
                }
            }
        }

        // 对属性节点进行排序，便于前端展示（对齐 linkTable）
        for (EdgeNodeVoV2 node : allNodeVos) {
            if (node.getAttributes() == null) {
                continue;
            }
            node.setAttributes(node.getAttributes().stream()
                    .sorted(Comparator.comparing(AssetNodeAttributesDto::getAttributeName))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        result.setNodeVos(allNodeVos);
        result.setEntityEdgeVos(allEntityEdgeVos);
        result.setAttributeEdgeVos(allAttributeEdgeVos);
        return result;
    }

    private void ensureNode(String urn, List<EdgeNodeVoV2> allNodeVos, Map<String, EdgeNodeVoV2> nodeVoMap) {
        if (nodeVoMap.get(urn) != null) {
            return;
        }
        EdgeNodeVoV2 nodeVo = new EdgeNodeVoV2();
        nodeVo.setNodeUrn(urn)
                .setNodeName(urn.substring(urn.lastIndexOf(SystemConstant.COLON) + 1));
        allNodeVos.add(nodeVo);
        nodeVoMap.put(urn, nodeVo);
    }

    private boolean isAttributeEdge(String urn, Map<String, AssetLineageNodeEntity> nodeMap) {
        if (!nodeMap.containsKey(urn) || nodeMap.get(urn) == null) {
            return false;
        }
        String nodeSubType = nodeMap.get(urn).getNodeSubType();
        return NodeSubTypeEnum.COLUMN.getNodeSubType().equals(nodeSubType)
                || NodeSubTypeEnum.METRIC.getNodeSubType().equals(nodeSubType);
    }

    /**
     * 对齐 AssetLineageEdgeService.processParentChildNodeNew 实现.
     */
    private void processParentChildNodeNew(String childUrn, String parentUrn, List<EdgeNodeVoV2> nodeVos, Map<String, EdgeNodeVoV2> nodeVoMap) {
        if (nodeVoMap.get(parentUrn) == null) {
            EdgeNodeVoV2 parentNodeVo = new EdgeNodeVoV2();
            parentNodeVo.setNodeUrn(parentUrn)
                    .setNodeName(parentUrn.substring(parentUrn.lastIndexOf(SystemConstant.COLON) + 1));
            nodeVos.add(parentNodeVo);
            nodeVoMap.put(parentUrn, parentNodeVo);

            Set<AssetNodeAttributesDto> subEdgeNodeVos = new HashSet<>();
            parentNodeVo.setAttributes(subEdgeNodeVos);

            subEdgeNodeVos.add(new AssetNodeAttributesDto()
                    .setAttributeName(childUrn.substring(childUrn.lastIndexOf(SystemConstant.COLON) + 1))
                    .setAttributeUrn(childUrn));
        } else {
            if (nodeVoMap.get(parentUrn).getAttributes() == null) {
                Set<AssetNodeAttributesDto> assetNodeAttributes = new HashSet<>();
                assetNodeAttributes.add(new AssetNodeAttributesDto()
                        .setAttributeName(childUrn.substring(childUrn.lastIndexOf(SystemConstant.COLON) + 1))
                        .setAttributeUrn(childUrn));
                nodeVoMap.get(parentUrn).setAttributes(assetNodeAttributes);
            }
            nodeVoMap.get(parentUrn).getAttributes().add(new AssetNodeAttributesDto()
                    .setAttributeName(childUrn.substring(childUrn.lastIndexOf(SystemConstant.COLON) + 1))
                    .setAttributeUrn(childUrn));
        }
    }

    /**
     * 批量获取 node 信息（最小实现：收集 source/target urn，再批量查询）.
     */
    private List<AssetLineageNodeEntity> getResourceNode(Map<Integer, List<LineageEdgeDto>> edgeMaps) {
        List<String> nodelists = new ArrayList<>();
        for (Map.Entry<Integer, List<LineageEdgeDto>> entry : edgeMaps.entrySet()) {
            List<LineageEdgeDto> edgeDtos = entry.getValue();
            for (LineageEdgeDto edgeDto : edgeDtos) {
                if (edgeDto == null) {
                    continue;
                }
                if (StringUtils.isNotBlank(edgeDto.getSourceUrn())) {
                    nodelists.add(edgeDto.getSourceUrn());
                }
                if (StringUtils.isNotBlank(edgeDto.getTargetUrn())) {
                    nodelists.add(edgeDto.getTargetUrn());
                }
            }
        }
        if (nodelists.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AssetLineageNodeEntity::getNodeUrn, nodelists);
        return nodeService.list(wrapper);
    }

    /**
     * 无边时的兜底：表模式返回单表节点；字段模式返回父表节点并带上当前列 attribute.
     */
    private LineEdgeNodeVoV3 emptyV3(String centerUrn, boolean isLeafNode) {
        LineEdgeNodeVoV3 result = new LineEdgeNodeVoV3();
        List<EdgeNodeVoV2> nodes = new ArrayList<>();
        if (!isLeafNode) {
            EdgeNodeVoV2 nodeVo = new EdgeNodeVoV2();
            nodeVo.setNodeUrn(centerUrn)
                    .setNodeName(centerUrn.substring(centerUrn.lastIndexOf(SystemConstant.COLON) + 1));
            nodes.add(nodeVo);
        } else {
            String tableUrn = getTableUrn(centerUrn);
            EdgeNodeVoV2 tableVo = new EdgeNodeVoV2();
            tableVo.setNodeUrn(tableUrn)
                    .setNodeName(tableUrn.substring(tableUrn.lastIndexOf(SystemConstant.COLON) + 1));
            Set<AssetNodeAttributesDto> attrs = new LinkedHashSet<>();
            AssetNodeAttributesDto col = new AssetNodeAttributesDto();
            col.setAttributeUrn(centerUrn);
            col.setAttributeName(centerUrn.substring(centerUrn.lastIndexOf(SystemConstant.COLON) + 1));
            attrs.add(col);
            tableVo.setAttributes(attrs);
            nodes.add(tableVo);
        }
        result.setNodeVos(nodes);
        result.setEntityEdgeVos(new ArrayList<>());
        result.setAttributeEdgeVos(new ArrayList<>());
        return result;
    }
}
