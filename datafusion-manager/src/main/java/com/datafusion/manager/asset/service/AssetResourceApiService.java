package com.datafusion.manager.asset.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.config.ResourceSyncConfig;
import com.datafusion.manager.asset.dto.ApiResourceInfoDto;
import com.datafusion.manager.asset.dto.builder.NodePropBuilder;
import com.datafusion.manager.asset.dto.builder.ResourceSnapshotBuilder;
import com.datafusion.manager.asset.dto.request.ApiBatchResourceReq;
import com.datafusion.manager.asset.dto.request.ApiResourceReq;
import com.datafusion.manager.asset.dto.response.ApiResourceResp;
import com.datafusion.manager.asset.dto.skywalking.CallEdge;
import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import com.datafusion.manager.asset.enums.NodeSubTypeEnum;
import com.datafusion.manager.asset.enums.NodeTypeEnum;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API资源服务实现类.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/22
 * @since 2026/01/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetResourceApiService extends BaseResourceService<ApiResourceInfoDto> {

    /**
     * 最大文件大小：10MB.
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 允许的文件扩展名.
     */
    private static final String ALLOWED_FILE_EXTENSION = ".json";

    /**
     * Skywalking链路处理服务.
     */
    @Autowired
    private SkywalkingTraceProcessingService skywalkingTraceProcessingService;

    /**
     * 同步配置.
     */
    @Autowired
    private final ResourceSyncConfig resourceSyncConfig;

    /**
     * 判断是否支持该资源类型.
     *
     * @param resourceType 资源类型枚举
     * @return true-支持，false-不支持
     */
    @Override
    boolean supports(ResourceTypeEnum resourceType) {
        return ResourceTypeEnum.API.equals(resourceType);
    }

    /**
     * API资源导入.
     *
     * @param resourceId     资源ID
     * @param apiResourceReq API资源导入请求体
     * @return 批量导入结果
     */
    public Boolean saveOrUpdateApiResource(UUID resourceId, ApiResourceReq apiResourceReq) {
        ApiResourceInfoDto apiResourceInfoDto = new ApiResourceInfoDto();
        BeanUtils.copyProperties(apiResourceReq, apiResourceInfoDto);
        super.saveOrUpdateResource(resourceId, apiResourceInfoDto);
        return true;
    }

    /**
     * 构建资源实体.
     * 子类根据业务数据构建 {@link AssetLineageResourceEntity}，
     * 包括设置资源名称、类型、快照等信息。
     *
     * @param contextData 上下文数据
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 资源实体
     */
    @Override
    protected AssetLineageResourceEntity buildResourceEntity(ApiResourceInfoDto contextData, Date now, String currentUser) {
        // 资源实体
        AssetLineageResourceEntity resourceMetricEntity = new AssetLineageResourceEntity();
        resourceMetricEntity
                .setResourceType(ResourceTypeEnum.API.getResouceType())
                .setResourceName(resourceName(contextData))
                .setResourceTag(ResourceTagEnum.NODE_AND_EDGE.getResourceTagType())
                .setResourceSnapshot(JacksonUtils.convertPojoToJsonNodeSafely(convertSnapshot(contextData)))
                .setStatus(ResourceStatusEnum.IMPORT_SUCCESS.getStatus())
                .setCreator(currentUser)
                .setUpdater(currentUser)
                .setCreateTime(now)
                .setUpdateTime(now);
        return resourceMetricEntity;
    }

    @Override
    protected @NotNull String resourceName(ApiResourceInfoDto contextData) {
        String resourceName = String.join(
                SystemConstant.COLON, contextData.getServiceEnName(), contextData.getRequestType(),
                (contextData.getBasePath() == null || contextData.getRequestUrl().startsWith(contextData.getBasePath()) ? ""
                        : contextData.getBasePath()) + contextData.getRequestUrl());
        return resourceName;
    }

    private ResourceSnapshotBuilder.ApiResourceSnapshot convertSnapshot(ApiResourceInfoDto apiResourceReq) {
        ResourceSnapshotBuilder.ApiResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(ResourceSnapshotBuilder.ResourceSnapshotType.API);
        BeanUtil.copyProperties(apiResourceReq, snapshot);
        snapshot.setRequestUrl(apiResourceReq.getRequestUrl());
        snapshot.setBasePath(apiResourceReq.getBasePath());
        snapshot.setServiceCnName(apiResourceReq.getServiceCnName());
        snapshot.setRequestUrlName(apiResourceReq.getRequestUrlName());
        return snapshot;
    }

    /**
     * API资源批量导入.
     *
     * @param file                上传的openapi json文件
     * @param apiBatchResourceReq 批量导入元数据
     * @return 批量导入结果
     */
    public Boolean apiResourceBatchImport(MultipartFile file, ApiBatchResourceReq apiBatchResourceReq) {
        validateFile(file);

        List<ApiResourceInfoDto> dtoList = new ArrayList<>();

        try {
            // 读取文件内容
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            // 解析OpenAPI结构
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(content);
            String basePath = rootNode.path("basePath").asText();
            JsonNode pathsNode = rootNode.path("paths");
            if (pathsNode.isMissingNode()) {
                throw new IllegalArgumentException("OpenAPI文件结构错误：缺少paths字段.");
            }
            String serviceCnName = rootNode.path("info").get("description").asText();

            // 遍历Paths
            Iterator<Map.Entry<String, JsonNode>> paths = pathsNode.fields();
            while (paths.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = paths.next();
                String requestUrl = pathEntry.getKey();
                JsonNode methodsNode = pathEntry.getValue();

                // 遍历HTTP Methods
                Iterator<Map.Entry<String, JsonNode>> methods = methodsNode.fields();
                while (methods.hasNext()) {

                    // 构建ApiResourceDto
                    ApiResourceInfoDto apiDto = new ApiResourceInfoDto();
                    BeanUtils.copyProperties(apiBatchResourceReq, apiDto);
                    apiDto.setRequestUrl(requestUrl);
                    apiDto.setBasePath(basePath);
                    Map.Entry<String, JsonNode> methodEntry = methods.next();
                    String requestType = methodEntry.getKey().toUpperCase();
                    JsonNode detailNode = methodEntry.getValue(); // 获取具体方法内部的详情节点

                    apiDto.setRequestType(requestType);
                    apiDto.setServiceCnName(serviceCnName);

                    // 1. 提取 Summary
                    String summary = detailNode.path("summary").asText("");
                    // 2. 提取 Tags (通常取第一个 tag)
                    String tag = "";
                    JsonNode tagsNode = detailNode.path("tags");
                    if (tagsNode.isArray() && tagsNode.size() > 0) {
                        tag = tagsNode.get(0).asText("");
                    }
                    // 3. 拼接 Tag 和 Summary 给 RequestUrlName 赋值
                    // 格式可以根据需求调整，例如 "边界数据管理-根据区域和时间查询数据是否存在"
                    String requestUrlName = tag + (tag.isEmpty() || summary.isEmpty() ? "" : "-") + summary;
                    apiDto.setRequestUrlName(requestUrlName);
                    dtoList.add(apiDto);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解析OpenAPI文件失败: " + e.getMessage(), e);
        }

        if (CollectionUtil.isEmpty(dtoList)) {
            log.info("没有找到有效的API资源.");
            return false;
        }

        // 批量保存API资源
        super.batchSaveResources(dtoList, new Date(), HttpUtils.getCurrentUserName());
        return true;
    }

    /**
     * API资源查询.
     *
     * @param resourceId 资源ID
     * @return 结果
     */
    public ApiResourceResp apiResourceQuery(UUID resourceId) {
        AssetLineageResourceEntity assetLineageResourceEntity = this.getBaseMapper().selectById(resourceId);
        ApiResourceResp resourceResp = new ApiResourceResp();
        ResourceSnapshotBuilder.ApiResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(ResourceSnapshotBuilder.ResourceSnapshotType.API);
        BeanUtils.copyProperties(snapshot, resourceResp);
        resourceResp.setResourceName(assetLineageResourceEntity.getResourceName());
        return resourceResp;
    }

    /**
     * 验证上传文件的有效性.
     *
     * @param file 上传的文件
     * @throws IllegalArgumentException 当文件验证失败时抛出
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空.");
        }

        // 验证文件大小（最大10MB）
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过10MB.");
        }

        // 验证文件内容类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/json")) {
            throw new IllegalArgumentException("仅支持JSON格式文件.");
        }

        // 验证文件名和扩展名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空.");
        }

        // 检查文件扩展名（不区分大小写）
        String filenameLower = originalFilename.toLowerCase();
        if (!filenameLower.endsWith(ALLOWED_FILE_EXTENSION)) {
            throw new IllegalArgumentException("文件必须是.json格式.");
        }

        // 验证文件名不包含路径遍历字符
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("文件名包含非法字符.");
        }
    }

    @Override
    protected List<AssetLineageNodeEntity> buildNodeEntities(ApiResourceInfoDto contextData, AssetLineageResourceEntity resource, Date now,
            String currentUser) {
        List<Triple<NodePropBuilder.ApiNodeProp, Set<MetricsTagDto>, NodePropBuilder.ApiNodeProp>> edges = getTriples(resource);
        List<ResourceSnapshotBuilder.WeLocation> weLocationList = getWeLocation(resource);

        if (CollectionUtils.isEmpty(edges)) {
            // 说明该API没有调用关系 获取自身作为节点即可
            return List.of(buildSelfNode(resource, now, currentUser, weLocationList));
        }

        Set<String> allUrns = new HashSet<>();
        List<AssetLineageNodeEntity> allNodes = new ArrayList<>();
        for (Triple<NodePropBuilder.ApiNodeProp, Set<MetricsTagDto>, NodePropBuilder.ApiNodeProp> edge : edges) {
            AssetLineageNodeEntity fromNode = buildNode(edge.getLeft(), allUrns, now, currentUser, weLocationList);
            if (fromNode != null) {
                allNodes.add(fromNode);
            }
            AssetLineageNodeEntity toNode = buildNode(edge.getRight(), allUrns, now, currentUser, weLocationList);
            if (toNode != null) {
                allNodes.add(toNode);
            }
        }
        return allNodes;
    }

    /**
     * 构建自身节点.
     *
     * @param resource    资源对象
     * @param now         当前时间
     * @param currentUser 当前用户
     * @param weLocationList 页面定位
     * @return 节点对象
     */
    public AssetLineageNodeEntity buildSelfNode(AssetLineageResourceEntity resource, Date now, String currentUser,
            List<ResourceSnapshotBuilder.WeLocation> weLocationList) {
        ResourceSnapshotBuilder.ApiResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(resource);
        NodePropBuilder.ApiNodeProp apiNodeProp = buildApiNodeProp(snapshot);
        return getAssetLineageNodeEntity(apiNodeProp, now, currentUser, weLocationList);
    }

    private @Nullable List<Triple<NodePropBuilder.ApiNodeProp, Set<MetricsTagDto>, NodePropBuilder.ApiNodeProp>> getTriples(
            AssetLineageResourceEntity resource) {
        ResourceSnapshotBuilder.ApiResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(resource);
        ResourceSnapshotBuilder.ApiResourceResultSnapshot resultSnapshot = ResourceSnapshotBuilder.builder(resource, true);
        String currentEndPoint = String.join(SystemConstant.COLON, snapshot.getServiceEnName(), snapshot.getRequestType(),
                (snapshot.getBasePath() == null || snapshot.getRequestUrl().startsWith(snapshot.getBasePath()) ? "" : snapshot.getBasePath())
                        + snapshot.getRequestUrl());

        if (resultSnapshot == null || resultSnapshot.getCallChain() == null || CollectionUtil.isEmpty(resultSnapshot.getCallChain().getCallEdges())) {
            log.info("解析没有生成边，资源ID: {}.", resource.getId());
            return Collections.emptyList();
        }
        // 取出当前接口往后的调用链，只保存后续的调用链
        List<CallEdge> filteredCallEdges = filterCallEdges(resultSnapshot.getCallChain().getCallEdges(), currentEndPoint);
        if (CollectionUtil.isEmpty(filteredCallEdges)) {
            log.info("过滤后没有调用边，资源ID: {}.", resource.getId());
            return Collections.emptyList();
        }

        log.info("开始持久化资源血缘，资源ID: {}，过滤后边数量: {}.", resource.getId(), filteredCallEdges.size());

        return convertEdges(filteredCallEdges, snapshot);
    }

    public List<ResourceSnapshotBuilder.WeLocation> getWeLocation(AssetLineageResourceEntity resource) {
        ResourceSnapshotBuilder.ApiResourceResultSnapshot resultSnapshot = ResourceSnapshotBuilder.builder(resource, true);
        if (resultSnapshot == null || resultSnapshot.getCallChain() == null) {
            return Collections.emptyList();
        }
        return resultSnapshot.getCallChain().getWeLocationList();

    }

    /**
     * 过滤掉后续的调用的边.
     *
     * @param callEdges 调用边列表
     * @param endPoint  目标端点
     * @return 过滤后的调用边列表
     */
    private List<CallEdge> filterCallEdges(List<CallEdge> callEdges, String endPoint) {
        List<CallEdge> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(callEdges)) {
            return result;
        }

        List<String> targetEndpoints = Lists.newArrayList();
        targetEndpoints.add(endPoint);
        // 之所以加上finish，是防止自己调用自己
        Set<String> finishEndpoints = new HashSet<>();
        while (targetEndpoints.size() > 0) {
            String currentEndPoint = targetEndpoints.get(0);
            if (finishEndpoints.contains(currentEndPoint)) {
                targetEndpoints.removeIf(v -> v.equals(currentEndPoint));
            } else {
                for (CallEdge edge : callEdges) {
                    String callerEndpoint = String.join(SystemConstant.COLON, edge.getCallerService(), edge.getCallerEndpoint());
                    String calleeEndpoint = String.join(SystemConstant.COLON, edge.getCalleeService(), edge.getCalleeEndpoint());
                    if (callerEndpoint.equals(currentEndPoint)) {
                        result.add(edge);
                        targetEndpoints.add(calleeEndpoint);
                    }
                }
                finishEndpoints.add(currentEndPoint);
                targetEndpoints.removeIf(v -> v.equals(currentEndPoint));
            }
        }
        return result;
    }

    /**
     * 构建节点.
     *
     * @param edge    边
     * @param allUrns 所有urn
     */
    private AssetLineageNodeEntity buildNode(NodePropBuilder.ApiNodeProp edge, Set<String> allUrns, Date now, String currentUser,
            List<ResourceSnapshotBuilder.WeLocation> weLocationList) {
        if (!allUrns.contains(edge.getUrn())) {
            AssetLineageNodeEntity node = getAssetLineageNodeEntity(edge, now, currentUser, weLocationList);
            allUrns.add(edge.getUrn());
            return node;
        }
        return null;
    }

    private @NotNull AssetLineageNodeEntity getAssetLineageNodeEntity(NodePropBuilder.ApiNodeProp edge, Date now,
            String currentUser, List<ResourceSnapshotBuilder.WeLocation> weLocationList) {
        AssetLineageNodeEntity nodeEntity = new AssetLineageNodeEntity();
        if (edge != null && CollectionUtil.isNotEmpty(weLocationList)) {
            edge.setWeLocationList(weLocationList);
        }
        nodeEntity.setNodeUrn(edge.getUrn());
        nodeEntity.setNodeName(edge.getUrl());
        nodeEntity.setNodeType(NodeTypeEnum.SERVICE.getNodeType());
        nodeEntity.setNodeSubType(NodeSubTypeEnum.API.getNodeSubType());
        nodeEntity.setNodeProp(JacksonUtils.convertPojoToJsonNodeSafely(edge));
        nodeEntity.setId(UUID.randomUUID());
        nodeEntity.setCreator(currentUser);
        nodeEntity.setCreateTime(now);
        nodeEntity.setUpdater(currentUser);
        nodeEntity.setUpdateTime(now);
        return nodeEntity;
    }

    @Override
    protected List<AssetLineageEdgeEntity> buildEdgeEntities(ApiResourceInfoDto contextData, AssetLineageResourceEntity resource,
            Date now, String currentUser) {
        List<Triple<NodePropBuilder.ApiNodeProp, Set<MetricsTagDto>, NodePropBuilder.ApiNodeProp>> edges = getTriples(resource);
        if (edges == null) {
            return Collections.emptyList();
        }
        List<AssetLineageEdgeEntity> edgeEntityList = new ArrayList<>();
        // 保存边
        for (Triple<NodePropBuilder.ApiNodeProp, Set<MetricsTagDto>, NodePropBuilder.ApiNodeProp> edge : edges) {
            String sourceUrn = edge.getRight().getUrn();
            String targetUrn = edge.getLeft().getUrn();
            JsonNode prop = null;
            //存储边的测点信息
            if (edge.getMiddle() != null) {
                prop = JacksonUtils.convertPojoToJsonNodeSafely(edge.getMiddle());
            }
            edgeEntityList.add(super.buildEdge(sourceUrn, targetUrn, resource, now, currentUser, prop));
        }
        return edgeEntityList;
    }

    // ===================== 辅助方法 =====================

    /**
     * 获取调用链边列表.
     *
     * @param callEdges 调用链边列表
     * @param snapshot 快照
     * @return 调用链边列表
     */
    public List<Triple<NodePropBuilder.ApiNodeProp, Set<MetricsTagDto>, NodePropBuilder.ApiNodeProp>> convertEdges(List<CallEdge> callEdges,
            ResourceSnapshotBuilder.ApiResourceSnapshot snapshot) {

        List<Triple<NodePropBuilder.ApiNodeProp, Set<MetricsTagDto>, NodePropBuilder.ApiNodeProp>> result = new ArrayList<>();
        for (CallEdge callEdge : callEdges) {
            // 构建源节点属性
            NodePropBuilder.ApiNodeProp fromProp = buildApiNodeProp(callEdge.getCallerService(), callEdge.getCallerEndpoint(), snapshot);

            // 构建目标节点属性
            NodePropBuilder.ApiNodeProp toProp = buildApiNodeProp(callEdge.getCalleeService(), callEdge.getCalleeEndpoint(), snapshot);

            // 创建边
            result.add(Triple.of(fromProp, callEdge.getTagSet(), toProp));
        }
        return result;
    }

    /**
     * 根据端点构建API节点属性.
     *
     * @param snapshot 端点，格式：方法:路径
     * @return API节点属性
     */
    private NodePropBuilder.ApiNodeProp buildApiNodeProp(ResourceSnapshotBuilder.ApiResourceSnapshot snapshot) {

        String serviceEnName = snapshot.getServiceEnName();
        String requestUrl = String.join(SystemConstant.COLON, snapshot.getRequestType(),
                (snapshot.getBasePath() == null || snapshot.getRequestUrl().startsWith(snapshot.getBasePath()) ? "" : snapshot.getBasePath())
                        + snapshot.getRequestUrl());
        // 构建URN
        String nodeUrn = buildNodeUrn(serviceEnName, String.join(SystemConstant.COLON, serviceEnName, requestUrl));
        // 构建节点属性
        NodePropBuilder.ApiNodeProp nodeProp = NodePropBuilder.builder(NodePropBuilder.NodePropType.API);
        nodeProp.setUrn(nodeUrn);
        nodeProp.setServiceCode(serviceEnName);
        nodeProp.setServiceName(snapshot.getServiceCnName());
        nodeProp.setUrl(requestUrl);
        nodeProp.setUrlName(snapshot.getRequestUrlName());
        return nodeProp;
    }

    /**
     * 根据端点构建API节点属性.
     *
     * @param requestUrl 端点，格式：方法:路径
     * @return API节点属性
     */
    private NodePropBuilder.ApiNodeProp buildApiNodeProp(String serviceEnName, String requestUrl,
            ResourceSnapshotBuilder.ApiResourceSnapshot snapshot) {
        // 构建URN
        String nodeUrn = buildNodeUrn(serviceEnName, String.join(SystemConstant.COLON, serviceEnName, requestUrl));
        // 构建节点属性
        NodePropBuilder.ApiNodeProp nodeProp = NodePropBuilder.builder(NodePropBuilder.NodePropType.API);
        nodeProp.setUrn(nodeUrn);
        nodeProp.setServiceCode(serviceEnName);

        nodeProp.setServiceName(serviceEnName);
        nodeProp.setUrl(requestUrl);
        nodeProp.setUrlName("");
        if (snapshot != null) {
            if (serviceEnName.equals(snapshot.getServiceEnName())) {
                nodeProp.setServiceName(snapshot.getServiceCnName());
                if (StringUtils.isNotEmpty(snapshot.getRequestUrl()) && requestUrl.endsWith(snapshot.getRequestUrl())) {
                    nodeProp.setUrlName(snapshot.getRequestUrlName());
                }
            }
        }
        return nodeProp;
    }

    /**
     * 构建节点URN.
     *
     * @param serviceEnName 服务英文名
     * @param requestUrl    端点
     * @return 节点URN
     */
    private String buildNodeUrn(String serviceEnName, String requestUrl) {
        return String.join(SystemConstant.COLON,
                resourceSyncConfig.getOrganization(),
                resourceSyncConfig.getVppByService(serviceEnName),
                resourceSyncConfig.getEnv(),
                resourceSyncConfig.getServiceType(),
                requestUrl);
    }
}
