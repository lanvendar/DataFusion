package com.datafusion.manager.asset.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.support.json.JSONUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.dao.MenuTmpMapper;
import com.datafusion.manager.asset.dto.MenuResourceInfoDto;
import com.datafusion.manager.asset.dto.builder.NodePropBuilder;
import com.datafusion.manager.asset.dto.builder.ResourceSnapshotBuilder;
import com.datafusion.manager.asset.dto.request.MenuResourceReq;
import com.datafusion.manager.asset.dto.response.ApiResourceNameResp;
import com.datafusion.manager.asset.dto.response.AppResp;
import com.datafusion.manager.asset.dto.response.MenuResourceResp;
import com.datafusion.manager.asset.dto.response.MenuResp;
import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import com.datafusion.manager.asset.enums.NodeSubTypeEnum;
import com.datafusion.manager.asset.enums.NodeTypeEnum;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.asset.po.MenuEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 菜单服务实现类.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2025/11/13
 * @since 2025/11/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetResourceMenuService extends BaseResourceService<MenuResourceInfoDto> {

    /**
     * 资源服务.
     */
    private final MenuTmpMapper menuTmpMapper;

    /**
     * 接口服务.
     */
    private final AssetResourceApiService apiResourceService;

    /**
     * 同步菜单数据到临时表.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void syncMenuToTmpDb() {
        try {
            // 清空临时表
            menuTmpMapper.truncateTable();
            // 同步数据
            menuTmpMapper.syncMenuFromDb();
            log.info("同步菜单数据到临时表完成");
        } catch (Exception e) {
            log.error("同步菜单数据到临时表失败", e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "同步菜单数据失败");
        }
    }

    /**
     * 从api资源的weLocation找到对应的菜单，并转为菜单资源.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void syncMenu() {
        syncMenu(null);
    }

    /**
     * 从api资源的weLocation找到对应的菜单，并转为菜单资源.
     * @param recorder 可选的未匹配菜单记录器，传入则持久化，不传则只记录日志
     */
    @Transactional(rollbackFor = Throwable.class)
    public void syncMenu(UnmatchedMenuRecorder recorder) {
        try {
            // 步骤1: 查询最近一天有weLocation的API资源
            List<AssetLineageResourceEntity> apiResources = this.getBaseMapper().selectApiResourcesWithWeLocation();
            if (apiResources == null || apiResources.isEmpty()) {
                log.info("未查询到需要同步的API资源");
                return;
            }

            // 步骤2: 提取 (projectName, weLocation) 组合
            // Map<(projectName, weLocation)组合, List<API资源>>
            Map<Pair<String, String>, Pair<List<AssetLineageResourceEntity>, Map<UUID, Set<MetricsTagDto>>>> comboToApiResources = new HashMap<>();
            // 用于批量查询菜单：Map<appCode, List<weLocation>>
            Map<String, List<String>> appCodeToWeLocations = new HashMap<>();

            for (AssetLineageResourceEntity apiResource : apiResources) {
                ResourceSnapshotBuilder.ApiResourceResultSnapshot snapshot = ResourceSnapshotBuilder.builder(apiResource, true);
                if (snapshot.getCallChain() == null || snapshot.getCallChain().getWeLocationList() == null
                        || snapshot.getCallChain().getWeLocationList().isEmpty()) {
                    continue;
                }

                // 遍历所有 weLocation
                for (ResourceSnapshotBuilder.WeLocation wl : snapshot.getCallChain().getWeLocationList()) {
                    String projectName = wl.getProjectName();
                    String weLocation = wl.getWeLocation();
                    Set<MetricsTagDto> tagSet = wl.getTagSet();
                    if (StrUtil.isBlank(projectName) || StrUtil.isBlank(weLocation)) {
                        continue;
                    }

                    String normalized = normalizeWeLocation(weLocation);
                    if (StrUtil.isBlank(normalized)) {
                        continue;
                    }

                    // 按 (projectName, normalizedWeLocation) 组合收集 API 资源
                    Pair<String, String> combo = Pair.of(projectName, normalized);
                    // 获取或初始化 Pair (Left 是 API 列表, Right 是 Tag 集合)
                    Pair<List<AssetLineageResourceEntity>, Map<UUID, Set<MetricsTagDto>>> dataPair = comboToApiResources.computeIfAbsent(combo,
                            k -> Pair.of(new ArrayList<>(), new HashMap<>()));

                    dataPair.getLeft().add(apiResource);
                    Map<UUID, Set<MetricsTagDto>> apiTagMap = dataPair.getRight();
                    if (CollectionUtil.isNotEmpty(wl.getTagSet())) {
                        apiTagMap.computeIfAbsent(apiResource.getId(), k -> new java.util.HashSet<>())
                                .addAll(wl.getTagSet());
                    } else {
                        apiTagMap.putIfAbsent(apiResource.getId(), new java.util.HashSet<>());
                    }

                    // 收集用于查询菜单
                    appCodeToWeLocations.computeIfAbsent(projectName, k -> new ArrayList<>()).add(normalized);
                }
            }

            if (comboToApiResources.isEmpty()) {
                log.info("未提取到有效的(projectName, weLocation)组合");
                return;
            }

            // 步骤3: 批量查询所有 (appCode, weLocation) 组合对应的菜单
            Map<Pair<String, String>, MenuEntity> menuComboMap = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : appCodeToWeLocations.entrySet()) {
                String appCode = entry.getKey();
                List<String> weLocations = entry.getValue().stream().distinct().collect(Collectors.toList());
                List<MenuEntity> menus = menuTmpMapper.selectMenusByAppCodeAndUrls(appCode, weLocations);
                for (MenuEntity menu : menus) {
                    menuComboMap.put(Pair.of(appCode, menu.getComponentUrl()), menu);
                }
            }

            // 查询所有菜单，构建ID->实体映射（用于构建菜单名称）
            Map<Long, MenuEntity> menuEntityMap = menuTmpMapper.selectList(new QueryWrapper<>()).stream()
                    .collect(Collectors.toMap(MenuEntity::getId, m -> m, (a, b) -> a));

            // 步骤4: 构建 (menuId, API资源信息) 关联，按 menuId 分组聚合
            // Map<menuId, MenuInfo>
            Map<Long, MenuInfo> menuInfoMap = new HashMap<>();

            // 收集未匹配的组合
            List<Pair<String, String>> unmatchedCombos = new ArrayList<>();
            List<UUID> unmatchedApiResourceIds = new ArrayList<>();

            for (Map.Entry<Pair<String, String>, Pair<List<AssetLineageResourceEntity>,
                    Map<UUID, Set<MetricsTagDto>>>> entry : comboToApiResources.entrySet()) {
                Pair<String, String> combo = entry.getKey();
                List<AssetLineageResourceEntity> apis = entry.getValue().getLeft();
                Map<UUID, Set<MetricsTagDto>> comboTagMap = entry.getValue().getRight();

                MenuEntity menu = menuComboMap.get(combo);
                if (menu == null) {
                    if (recorder != null) {
                        // 收集未匹配的组合
                        unmatchedCombos.add(combo);
                        unmatchedApiResourceIds.add(apis.isEmpty() ? null : apis.get(0).getId());
                    }
                    continue;
                }

                // 取第一个 API 的快照信息作为模板
                ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot = ResourceSnapshotBuilder.builder(apis.get(0));

                MenuInfo menuInfo = menuInfoMap.computeIfAbsent(menu.getId(), k -> {
                    MenuInfo info = new MenuInfo();
                    info.setMenu(menu);
                    info.setApiSnapshot(apiSnapshot);
                    return info;
                });

                // 步骤4: 每个 API 资源在该 combo 下有独立的 tagSet
                for (AssetLineageResourceEntity api : apis) {
                    Set<MetricsTagDto> tagsForApi = comboTagMap.getOrDefault(api.getId(), new java.util.HashSet<>());
                    if (CollectionUtil.isNotEmpty(tagsForApi)) {
                        menuInfo.getApiResourceIds()
                                .computeIfAbsent(api.getId(), k -> new java.util.HashSet<>())
                                .addAll(tagsForApi);
                    } else {
                        menuInfo.getApiResourceIds().putIfAbsent(api.getId(), new java.util.HashSet<>());
                    }
                }
            }

            // 记录未匹配的菜单（注入则持久化，否则只记录日志）
            if (recorder != null && !unmatchedCombos.isEmpty()) {
                recorder.record(unmatchedCombos, unmatchedApiResourceIds);
            }

            // 步骤5: 遍历分组后的菜单，组装数据并保存
            for (MenuInfo menuInfo : menuInfoMap.values()) {
                try {
                    MenuEntity menu = menuInfo.getMenu();
                    ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot = menuInfo.getApiSnapshot();

                    // 构建菜单名称（拼接父级路径）
                    String menuName = buildMenuNameWithParent(menu.getId(), menu.getName(), menuEntityMap);

                    // 组装请求体
                    MenuResourceReq req = new MenuResourceReq();
                    req.setOrganization(apiSnapshot.getOrganization());
                    req.setBusinessDomain(apiSnapshot.getBusinessDomain());
                    req.setAppCode(menu.getAppCode());
                    req.setAppName(menu.getAppName());
                    req.setMenuId(menu.getId());
                    req.setComponentType(menu.getComponentType());
                    req.setMenuName(menuName);
                    req.setApiResourceIds(menuInfo.getApiResourceIds());

                    // 调用保存方法
                    saveOrUpdateMenu(null, req);
                } catch (Exception e) {
                    log.warn("处理菜单资源失败, menuId: {}, 原因: {}", menuInfo.getMenu().getId(), e.getMessage());
                }
            }

            log.info("同步菜单资源完成, 共处理 {} 个菜单", menuInfoMap.size());
        } catch (Exception e) {
            log.error("同步菜单资源失败", e);
        }
    }

    /**
     * 菜单信息封装类.
     */
    private static class MenuInfo {
        /**
         * 菜单信息.
         */
        private MenuEntity menu;

        /**
         * apiSnapshot.
         */
        private ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot;

        /**
         * apiResourceIds.
         */
        private Map<UUID, Set<MetricsTagDto>> apiResourceIds = new HashMap<>();

        public MenuEntity getMenu() {
            return menu;
        }

        public void setMenu(MenuEntity menu) {
            this.menu = menu;
        }

        public ResourceSnapshotBuilder.ApiResourceSnapshot getApiSnapshot() {
            return apiSnapshot;
        }

        public void setApiSnapshot(ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot) {
            this.apiSnapshot = apiSnapshot;
        }

        public Map<UUID, Set<MetricsTagDto>> getApiResourceIds() {
            return apiResourceIds;
        }

        public void setApiResourceIds(Map<UUID, Set<MetricsTagDto>> apiResourceIds) {
            this.apiResourceIds = apiResourceIds;
        }
    }

    /**
     * 构建带父级路径的菜单名称.
     *
     * @param menuId        菜单ID
     * @param menuName      当前菜单名称
     * @param menuEntityMap 菜单实体映射
     * @return 带父级路径的菜单名称，如：菜单1-菜单2-菜单3
     */
    private String buildMenuNameWithParent(Long menuId, String menuName, Map<Long, MenuEntity> menuEntityMap) {
        // 从当前菜单向上查找父级，构建路径
        List<String> namePath = new ArrayList<>();
        namePath.add(menuName);

        MenuEntity current = menuEntityMap.get(menuId);
        while (current != null && current.getParentId() != null) {
            MenuEntity parent = menuEntityMap.get(current.getParentId());
            if (parent != null) {
                namePath.add(0, parent.getName());
                current = parent;
            } else {
                break;
            }
        }

        return StrUtil.join(SystemConstant.COMMA, namePath);
    }

    /**
     * 标准化weLocation.
     * 1. 最左边如果没有"/"需要添加"/";
     * 2. 去除？后面的字符串，包括？;
     * 3. 去除末尾的数字ID，如/inverter-device/1823730852270571520 -> /inverter-device
     *
     * @param weLocation 原始weLocation
     * @return 标准化后的weLocation
     */
    private String normalizeWeLocation(String weLocation) {
        if (StrUtil.isBlank(weLocation)) {
            return weLocation;
        }
        // 1. 去除?后面的字符串
        int questionMarkIndex = weLocation.indexOf('?');

        if (questionMarkIndex != -1) {
            weLocation = weLocation.substring(0, questionMarkIndex);
        }
        // 2. 左边添加"/"
        if (!weLocation.startsWith("/")) {
            weLocation = "/" + weLocation;
        }
        // 3. 去除末尾的数字ID（/后面全是数字）
        if (weLocation.matches(".+/\\d+$")) {
            weLocation = weLocation.replaceAll("/\\d+$", "");
        }
        return weLocation;
    }

    /**
     * 获取应用名称列表.
     *
     * @return 应用名称列表
     */
    public List<AppResp> getAppNameList() {
        return menuTmpMapper.getAppNameList();
    }

    /**
     * 获取菜单树形结构.
     *
     * @param appCode 应用Code
     * @return 菜单树形结构
     */
    public List<MenuResp> getMenuTreeByAppCode(String appCode) {
        List<MenuEntity> menuList = menuTmpMapper.getMenuTreeByAppCode(appCode);
        // 构建树形结构
        return buildMenuTree(menuList, null);
    }

    /**
     * 构建菜单树形结构.
     *
     * @param menuList 菜单列表
     * @param parentId 父菜单ID
     * @return 树形菜单列表
     */
    private List<MenuResp> buildMenuTree(List<MenuEntity> menuList, Long parentId) {
        return menuList.stream()
                .filter(menu -> Objects.equals(menu.getParentId(), parentId))
                .map(menu -> {
                    // 递归设置子菜单
                    MenuResp menuResp = new MenuResp();
                    menuResp.setMenuId(menu.getId());
                    menuResp.setMenuName(menu.getName());
                    menuResp.setComponentUrl(menu.getComponentUrl());
                    menuResp.setComponentType(menu.getComponentType());
                    menuResp.setChildren(buildMenuTree(menuList, menu.getId()));
                    return menuResp;
                })
                .collect(Collectors.toList());
    }

    /**
     * 查询菜单数据.
     *
     * @param resourceId 资源ID
     * @return 菜单资源响应
     */
    public MenuResourceResp getMenu(UUID resourceId) {
        try {
            AssetLineageResourceEntity resource = this.getBaseMapper().selectById(resourceId);
            if (resource == null) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源不存在");
            }
            MenuResourceResp resp = new MenuResourceResp();
            resp.setResourceId(resource.getId());
            ResourceSnapshotBuilder.MenuResourceSnapshot menuSnapshot = ResourceSnapshotBuilder.builder(resource);
            ResourceSnapshotBuilder.MenuResourceResultSnapshot menuResultSnapshot = ResourceSnapshotBuilder.builder(resource, true);
            BeanUtil.copyProperties(menuSnapshot, resp);
            resp.setApiResources(new ArrayList<>());
            Map<UUID, Set<MetricsTagDto>> apiResourceMaps = menuResultSnapshot.getApiResourceIds();
            List<UUID> apiResourceIds = new ArrayList<>(apiResourceMaps.keySet());
            // 查询API资源名称和请求信息
            super.baseMapper.selectBatchIds(apiResourceIds).forEach(apiResource -> {
                ApiResourceNameResp nameResp = new ApiResourceNameResp();
                nameResp.setResourceId(apiResource.getId());
                nameResp.setResourceName(apiResource.getResourceName());
                ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot = ResourceSnapshotBuilder.builder(apiResource);
                nameResp.setRequestUrl(apiSnapshot.getRequestUrl());
                nameResp.setRequestType(apiSnapshot.getRequestType());
                resp.getApiResources().add(nameResp);
            });
            return resp;
        } catch (Exception e) {
            log.error("查询菜单数据失败, resourceId: " + resourceId, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "查询菜单资源数据失败");
        }
    }

    /**
     * 更新菜单数据.
     *
     * @param resourceId  资源ID
     * @param resourceReq 请求体
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateMenu(UUID resourceId, MenuResourceReq resourceReq) {
        try {
            MenuResourceInfoDto menuResourceInfoDto = new MenuResourceInfoDto();
            BeanUtil.copyProperties(resourceReq, menuResourceInfoDto);
            super.saveOrUpdateResource(resourceId, menuResourceInfoDto);
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("新增/更新菜单数据失败, 请求体: " + JSONUtils.toJSONString(resourceReq), e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "处理资源数据失败");
        }
    }

    /**
     * 删除菜单数据.
     *
     * @param resourceId 资源ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(UUID resourceId) {
        try {
            AssetLineageResourceEntity resource = this.getBaseMapper().selectById(resourceId);
            if (resource == null) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源不存在");
            }
            super.deleteResource(resourceId);
        } catch (Exception e) {
            log.error("删除菜单数据失败, resourceId: " + resourceId, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "删除菜单数据失败");
        }
    }

    /**
     * 构建菜单资源名称.
     *
     * @param req 请求体
     * @return 菜单资源名称
     */
    @Override
    public String resourceName(MenuResourceInfoDto req) {
        return StrUtil.join(SystemConstant.COLON, req.getAppCode(), req.getMenuName());
    }

    /**
     * 判断是否支持该资源类型.
     *
     * @param resourceType 资源类型枚举
     * @return true-支持，false-不支持
     */
    @Override
    boolean supports(ResourceTypeEnum resourceType) {
        return ResourceTypeEnum.GUI.equals(resourceType);
    }

    /**
     * 构建资源实体.
     * 子类根据业务数据构建 {@link AssetLineageResourceEntity}，
     * 包括设置资源名称、类型、快照等信息。
     * 注意：构建实体时，如果id存在，则认为是更新，否则是创建，因此创建实体时，id必须为空。
     * @param contextData 上下文数据
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 资源实体
     */
    @Override
    protected AssetLineageResourceEntity buildResourceEntity(MenuResourceInfoDto contextData, Date now, String currentUser) {
        // 构建菜单快照
        ResourceSnapshotBuilder.MenuResourceSnapshot menuSnapshot = new ResourceSnapshotBuilder.MenuResourceSnapshot();
        ResourceSnapshotBuilder.MenuResourceResultSnapshot menuResultSnapshot = new ResourceSnapshotBuilder.MenuResourceResultSnapshot();
        menuResultSnapshot.setApiResourceIds(contextData.getApiResourceIds());
        menuSnapshot.setAppCode(contextData.getAppCode());
        menuSnapshot.setAppName(contextData.getAppName());
        menuSnapshot.setMenuId(contextData.getMenuId());
        menuSnapshot.setMenuName(contextData.getMenuName());
        menuSnapshot.setOrganization(contextData.getOrganization());
        menuSnapshot.setComponentType(contextData.getComponentType());
        menuSnapshot.setBusinessDomain(contextData.getBusinessDomain());
        String resourceName = resourceName(contextData);
        AssetLineageResourceEntity entity = new AssetLineageResourceEntity();
        entity.setResourceName(resourceName);
        entity.setResourceType(ResourceTypeEnum.GUI.getResouceType());
        entity.setResourceTag(ResourceTagEnum.NODE_AND_EDGE.getResourceTagType());
        entity.setResourceSnapshot(JacksonUtils.pojo2JsonNodeOrNull(menuSnapshot));
        entity.setResultSnapshot(JacksonUtils.pojo2JsonNodeOrNull(menuResultSnapshot));
        // 菜单默认直接就是导入血缘成功
        entity.setStatus(ResourceStatusEnum.PARSE_SUCCESS.getStatus());
        entity.setCreator(currentUser);
        entity.setUpdater(currentUser);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    /**
     * 构建节点实体列表.
     * 节点和边由页面点击导入血缘按钮时保存，此处返回空列表.
     *
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 空节点列表
     */
    @Override
    protected List<AssetLineageNodeEntity> buildNodeEntities(MenuResourceInfoDto contextData, AssetLineageResourceEntity resource, Date now,
            String currentUser) {
        List<AssetLineageNodeEntity> nodeEntities = new ArrayList<>();
        nodeEntities.add(buildSelfNode(resource, now, currentUser));
        ResourceSnapshotBuilder.MenuResourceResultSnapshot snapshot = ResourceSnapshotBuilder.builder(resource, true);
        List<AssetLineageResourceEntity> apiResourceEntities = super.baseMapper
                .selectBatchIds(new ArrayList<>(snapshot.getApiResourceIds().keySet()));
        apiResourceEntities.forEach(apiResourceEntity -> {
            // api的node也加进来，如果已经存在，后续处理会忽略
            List<ResourceSnapshotBuilder.WeLocation> weLocationList = apiResourceService.getWeLocation(apiResourceEntity);
            nodeEntities.add(apiResourceService.buildSelfNode(apiResourceEntity, now, currentUser, weLocationList));
        });
        return nodeEntities;
    }

    /**
     * 构建自身节点.
     *
     * @param resource    资源对象
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 节点对象
     */
    public AssetLineageNodeEntity buildSelfNode(AssetLineageResourceEntity resource, Date now, String currentUser) {
        ResourceSnapshotBuilder.MenuResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(resource);

        NodePropBuilder.MenuNodeProp menuNodeProp = NodePropBuilder.builder(NodePropBuilder.NodePropType.MENU);
        String urn = String.join(SystemConstant.COLON, snapshot.getOrganization(),
                snapshot.getBusinessDomain(),
                snapshot.getAppCode(), snapshot.getMenuName());
        menuNodeProp.setUrn(urn);
        menuNodeProp.setAppCode(snapshot.getAppCode());
        menuNodeProp.setAppName(snapshot.getAppName());
        menuNodeProp.setMenu(snapshot.getMenuName());
        menuNodeProp.setComponentType(snapshot.getComponentType());

        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(urn);
        node.setNodeName(snapshot.getMenuName());
        node.setNodeType(NodeTypeEnum.GUI.getNodeType());
        node.setNodeSubType(NodeSubTypeEnum.MENU.getNodeSubType());
        node.setNodeProp(JacksonUtils.pojo2JsonNodeOrNull(menuNodeProp));
        node.setCreator(currentUser);
        node.setCreateTime(now);
        node.setUpdater(currentUser);
        node.setUpdateTime(now);
        return node;
    }

    /**
     * 构建边实体列表.
     * 节点和边由页面点击导入血缘按钮时保存，此处返回空列表.
     *
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 空边列表
     */
    @Override
    protected List<AssetLineageEdgeEntity> buildEdgeEntities(MenuResourceInfoDto contextData, AssetLineageResourceEntity resource, Date now,
            String currentUser) {
        List<AssetLineageEdgeEntity> edgeEntityList = new ArrayList<>();
        AssetLineageNodeEntity selfNode = buildSelfNode(resource, now, currentUser);
        String selfUrn = selfNode.getNodeUrn();
        ResourceSnapshotBuilder.MenuResourceResultSnapshot snapshot = ResourceSnapshotBuilder.builder(resource, true);
        List<AssetLineageResourceEntity> apiResourceEntities = super.baseMapper
                .selectBatchIds(snapshot.getApiResourceIds().keySet().stream()
                        .collect(Collectors.toList()));
        apiResourceEntities.forEach(apiResourceEntity -> {
            List<ResourceSnapshotBuilder.WeLocation> weLocationList = apiResourceService.getWeLocation(apiResourceEntity);
            AssetLineageNodeEntity apiNode = apiResourceService.buildSelfNode(apiResourceEntity, now, currentUser, weLocationList);
            AssetLineageEdgeEntity edge = new AssetLineageEdgeEntity();
            edge.setId(UUID.randomUUID());
            edge.setSourceUrn(apiNode.getNodeUrn());
            edge.setTargetUrn(selfUrn);
            edge.setResourceId(resource.getId());
            edge.setCreator(currentUser);
            edge.setCreateTime(now);
            edge.setUpdater(currentUser);
            edge.setUpdateTime(now);
            Set<MetricsTagDto> tagInfo = snapshot.getApiResourceIds().get(apiResourceEntity.getId());
            if (CollectionUtil.isNotEmpty(tagInfo)) {
                edge.setEdgeProp(wrapTagSet(JacksonUtils.pojo2JsonNodeOrNull(tagInfo)));
            }
            edgeEntityList.add(edge);
        });
        return edgeEntityList;
    }
}
