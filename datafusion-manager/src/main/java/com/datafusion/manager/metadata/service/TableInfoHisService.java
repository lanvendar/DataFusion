package com.datafusion.manager.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import com.datafusion.manager.metadata.po.TableInfoHisIdEntity;

import java.util.List;
import java.util.UUID;

/**
 * 元数据-表历史版本服务.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
public interface TableInfoHisService extends IService<TableInfoHisIdEntity> {

    /**
     * 自定义数据更新操作（存在就更新，不存在就插入）.
     * @param tableInfoSnapshotList 当前表的快照信息
     * @param version 版本信息
     * @return boolean
     */
    boolean customSaveOrUpdateBatch(List<TableInfoEntity> tableInfoSnapshotList, String version);

    /**
     * saveOrUpdateBatch.
     * @param entityList entityList
     * @return boolean
     */
    boolean saveOrUpdateBatch(List<TableInfoHisIdEntity> entityList);

    /**
     * 根据表ID查询字段.
     *
     * @param tableId 表ID
     * @return 字段列表
     */
    List<TableInfoHisIdEntity> getByTableId(UUID tableId);

    /**
     * 根据表ID集合查询字段.
     *
     * @param tableIds 表ID集合
     * @return 字段列表
     */
    List<TableInfoHisIdEntity> getByTableIds(List<UUID> tableIds);

    /**
     * 根据表ID删除历史版本信息.
     *
     * @param tableId 表ID
     * @return 删除结果
     */
    boolean deleteByTableId(UUID tableId);

}
