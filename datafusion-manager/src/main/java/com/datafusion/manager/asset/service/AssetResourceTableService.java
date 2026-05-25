package com.datafusion.manager.asset.service;

import cn.hutool.core.bean.BeanUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.dto.DbTableSnapshot;
import com.datafusion.manager.asset.dto.TableResourceInfoDto;
import com.datafusion.manager.asset.dto.request.TableResourceReq;
import com.datafusion.manager.asset.dto.response.TableResourceResp;
import com.datafusion.manager.asset.enums.NodeSubTypeEnum;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.metadata.dto.ColumnTreeDto;
import com.datafusion.manager.metadata.dto.TableColumnsTreeDto;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 库表资源服务.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/02/28
 * @since 2026/02/28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetResourceTableService extends BaseResourceService<TableResourceInfoDto> {

    /**
     * 库表资源导入.
     * 流程：
     * 1. 页面调用 /table/import 保存资源（状态=解析成功）
     * 2. 页面调用 /enter/lineage/{id} 录入血缘（生成节点和边）
     *
     * @param tableResourceReq 库表资源导入请求
     * @return 是否导入成功
     */
    @Transactional(rollbackFor = Throwable.class)
    public Boolean tableImport(TableResourceReq tableResourceReq) {
        try {
            // 将请求转换为上下文数据列表（一个表对应一个上下文）
            List<TableResourceInfoDto> contextList = convertToContextList(tableResourceReq);
            if (contextList == null || contextList.isEmpty()) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "没有有效的表数据");
            }

            // 批量保存资源
            Date now = new Date();
            String currentUser = HttpUtils.getCurrentUserName();
            batchSaveResources(contextList, now, currentUser);

            log.info("库表资源导入成功, 共 {} 个表", contextList.size());
            return true;
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("库表资源导入失败, 请求体: {}", tableResourceReq, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "库表资源导入失败");
        }
    }

    /**
     * 将请求转换为上下文数据列表.
     *
     * @param req 请求体
     * @return 上下文数据列表
     */
    private List<TableResourceInfoDto> convertToContextList(TableResourceReq req) {
        List<TableResourceInfoDto> contextList = new ArrayList<>();
        for (TableColumnsTreeDto tableColumn : req.getTableColumns()) {
            TableResourceInfoDto context = new TableResourceInfoDto();
            BeanUtil.copyProperties(req, context);
            context.setTableColumn(tableColumn);
            contextList.add(context);
        }
        return contextList;
    }

    /**
     * 判断是否支持该资源类型.
     *
     * @param resourceType 资源类型枚举
     * @return true-支持，false-不支持
     */
    @Override
    protected boolean supports(ResourceTypeEnum resourceType) {
        return ResourceTypeEnum.DATABASE.equals(resourceType);
    }

    /**
     * 获取资源名称.
     *
     * @param contextData 上下文数据
     * @return 资源名称
     */
    @Override
    protected String resourceName(TableResourceInfoDto contextData) {
        return contextData.getTableColumn().getTableName();
    }

    /**
     * 构建资源实体.
     *
     * @param contextData  上下文数据
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 资源实体
     */
    @Override
    protected AssetLineageResourceEntity buildResourceEntity(TableResourceInfoDto contextData, Date now, String currentUser) {
        TableColumnsTreeDto tableColumn = contextData.getTableColumn();

        // 构建快照
        DbTableSnapshot snapshot = new DbTableSnapshot();
        BeanUtil.copyProperties(contextData, snapshot);
        BeanUtil.copyProperties(tableColumn, snapshot);

        AssetLineageResourceEntity entity = new AssetLineageResourceEntity();
        entity.setResourceName(tableColumn.getTableName());
        entity.setResourceType(ResourceTypeEnum.DATABASE.getResouceType());
        entity.setResourceTag(ResourceTagEnum.NODE.getResourceTagType());
        entity.setResourceSnapshot(JacksonUtils.convertPojoToJsonNodeSafely(snapshot));
        // 库表资源默认直接就是导入血缘成功
        entity.setStatus(ResourceStatusEnum.PARSE_SUCCESS.getStatus());
        entity.setCreator(currentUser);
        entity.setUpdater(currentUser);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    /**
     * 构建节点实体列表.
     * 包含表节点和字段节点.
     *
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 节点实体列表
     */
    @Override
    protected List<AssetLineageNodeEntity> buildNodeEntities(TableResourceInfoDto contextData, AssetLineageResourceEntity resource, Date now,
            String currentUser) {
        List<AssetLineageNodeEntity> nodeList = new ArrayList<>();

        DbTableSnapshot snapshot = JacksonUtils.convertJsonNodeToPojoSafely(resource.getResourceSnapshot(), DbTableSnapshot.class);
        String tableName = snapshot.getTableName();

        // 构建表节点
        String nodeUrn = buildTableUrn(snapshot, tableName);
        AssetLineageNodeEntity tableNode = buildTableNode(nodeUrn, tableName, snapshot, now, currentUser);
        nodeList.add(tableNode);

        // 构建字段节点
        //        if (snapshot.getColumns() != null) {
        //            for (ColumnTreeDto column : snapshot.getColumns()) {
        //                String columnUrn = buildColumnUrn(nodeUrn, column.getColumnName());
        //                AssetLineageNodeEntity columnNode = buildColumnNode(columnUrn, column, now, currentUser);
        //                nodeList.add(columnNode);
        //            }
        //        }

        return nodeList;
    }

    /**
     * 构建边实体列表.
     * 表 → 字段的边关系.
     *
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 边实体列表
     */
    @Override
    protected List<AssetLineageEdgeEntity> buildEdgeEntities(TableResourceInfoDto contextData, AssetLineageResourceEntity resource, Date now,
            String currentUser) {
        List<AssetLineageEdgeEntity> edgeList = new ArrayList<>();

        return edgeList;
    }

    /**
     * 构建表节点URN.
     */
    private String buildTableUrn(DbTableSnapshot snapshot, String tableName) {
        return String.join(SystemConstant.COLON, snapshot.getDatabaseType(),
                snapshot.getDatasourceName(),
                snapshot.getDatabaseName(),
                snapshot.getSchemaName(),
                tableName);
    }

    /**
     * 构建字段节点URN.
     */
    private String buildColumnUrn(String tableUrn, String columnName) {
        return String.join(SystemConstant.COLON, tableUrn, columnName);
    }

    /**
     * 构建表节点.
     */
    private AssetLineageNodeEntity buildTableNode(String nodeUrn, String tableName,
            DbTableSnapshot snapshot,
            Date now, String currentUser) {
        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(tableName);
        node.setNodeType(ResourceTypeEnum.DATABASE.getResouceType());
        node.setNodeSubType(NodeSubTypeEnum.TABLE.getNodeSubType());
        node.setCreator(currentUser);
        node.setCreateTime(now);
        node.setUpdater(currentUser);
        node.setUpdateTime(now);
        return node;
    }

    /**
     * 构建字段节点.
     */
    private AssetLineageNodeEntity buildColumnNode(String nodeUrn, ColumnTreeDto column,
            Date now, String currentUser) {
        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(column.getColumnName());
        node.setNodeType(ResourceTypeEnum.DATABASE.getResouceType());
        node.setNodeSubType(NodeSubTypeEnum.COLUMN.getNodeSubType());
        node.setCreator(currentUser);
        node.setCreateTime(now);
        node.setUpdater(currentUser);
        node.setUpdateTime(now);
        return node;
    }

    /**
     * 查询库表资源.
     *
     * @param resourceId 资源ID
     * @return 库表资源响应
     */
    public TableResourceResp getTable(UUID resourceId) {
        try {
            AssetLineageResourceEntity resource = this.getBaseMapper().selectById(resourceId);
            if (resource == null) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源不存在");
            }

            TableResourceResp resp = new TableResourceResp();
            resp.setResourceId(resource.getId());

            DbTableSnapshot snapshot = JacksonUtils.convertJsonNodeToPojoSafely(
                    resource.getResourceSnapshot(), DbTableSnapshot.class);
            BeanUtil.copyProperties(snapshot, resp);

            // 转换列信息
            if (snapshot.getColumns() != null) {
                List<TableResourceResp.ColumnResp> columnRespList = new ArrayList<>();
                for (ColumnTreeDto column : snapshot.getColumns()) {
                    TableResourceResp.ColumnResp columnResp = new TableResourceResp.ColumnResp();
                    columnResp.setColumnName(column.getColumnName());
                    columnResp.setColumnDesc(column.getColumnDesc());
                    columnResp.setColumnType(column.getColumnType());
                    columnRespList.add(columnResp);
                }
                resp.setColumns(columnRespList);
            }

            return resp;
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询库表数据失败, resourceId: " + resourceId, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "查询库表资源数据失败");
        }
    }

    /**
     * 更新库表资源.
     *
     * @param resourceId      资源ID
     * @param tableResourceReq 请求体
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateTable(UUID resourceId, TableResourceReq tableResourceReq) {
        try {
            TableResourceInfoDto tableResourceInfoDto = new TableResourceInfoDto();
            BeanUtil.copyProperties(tableResourceReq, tableResourceInfoDto);
            // 只取第一个表进行更新
            if (tableResourceReq.getTableColumns() != null && !tableResourceReq.getTableColumns().isEmpty()) {
                tableResourceInfoDto.setTableColumn(tableResourceReq.getTableColumns().get(0));
            }
            super.saveOrUpdateResource(resourceId, tableResourceInfoDto);
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新库表数据失败, resourceId: " + resourceId, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "更新库表资源数据失败");
        }
    }

}
