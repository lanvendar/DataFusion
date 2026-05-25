package com.datafusion.manager.metadata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.manager.metadata.dao.ColumnInfoHisMapper;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.datafusion.manager.metadata.po.ColumnInfoHisIdEntity;
import com.datafusion.manager.metadata.service.ColumnInfoHisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 元数据-字段信历史息服务.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
@Slf4j
@Service
public class ColumnInfoHisServiceImpl extends ServiceImpl<ColumnInfoHisMapper, ColumnInfoHisIdEntity>
        implements ColumnInfoHisService {

    @Override
    public boolean customSaveOrUpdateBatch(List<ColumnInfoEntity> columnInfoSnapshotList, String version) {
        // 首先将TableInfoEntity 转成 TableInfoHisEntity
        List<ColumnInfoHisIdEntity> hisList = columnInfoSnapshotList.stream().map(dto -> {
            ColumnInfoHisIdEntity vo = new ColumnInfoHisIdEntity();
            BeanUtils.copyProperties(dto, vo);
            // 赋值版本信息
            vo.setVersion(version);
            // 赋值uuid
            // vo.setId(IdGenerator.createColumnId(vo.getTableId(), vo.getColumnName() + vo.getVersion()));
            return vo;
        }).collect(Collectors.toList());
        return saveOrUpdateBatch(hisList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateBatch(List<ColumnInfoHisIdEntity> entityList) {
        if (CollectionUtils.isEmpty(entityList)) {
            return true;
        }

        // --- 步骤 1: 构造查询条件，基于 (id, version) 复合主键 ---

        // 查询条件变为 WHERE (id = ? AND version = ?) OR (...) ...
        QueryWrapper<ColumnInfoHisIdEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> {
            for (ColumnInfoHisIdEntity entity : entityList) {
                // 【修正】: 只使用 id 和 version 构建查询条件
                wrapper.or(w -> w.eq("id", entity.getId())
                        .eq("version", entity.getVersion()));
            }
        });

        // 执行一次查询，获取所有已存在的记录
        List<ColumnInfoHisIdEntity> existingEntities = this.list(queryWrapper);

        // 将已存在的记录转换为 Map，Key 依然是 "id_version"
        Map<String, ColumnInfoHisIdEntity> existingMap = existingEntities.stream()
                .collect(Collectors.toMap(
                        entity -> buildCompositeKey(entity), // 使用修正后的辅助方法
                        entity -> entity
                ));

        // --- 步骤 2: 分组 ---

        List<ColumnInfoHisIdEntity> toInsertList = new ArrayList<>();
        List<ColumnInfoHisIdEntity> toUpdateList = new ArrayList<>();

        for (ColumnInfoHisIdEntity entity : entityList) {
            String compositeKey = buildCompositeKey(entity);
            if (existingMap.containsKey(compositeKey)) {
                toUpdateList.add(entity);
            } else {
                toInsertList.add(entity);
            }
        }

        // --- 步骤 3: 执行批量操作 ---

        // A. 批量插入
        if (!toInsertList.isEmpty()) {
            this.saveBatch(toInsertList);
        }

        // B. 批量更新
        if (!toUpdateList.isEmpty()) {
            for (ColumnInfoHisIdEntity entityToUpdate : toUpdateList) {
                this.update(
                        entityToUpdate, // 这里的 entityToUpdate 包含了最新的 tableId 和其他字段值
                        new QueryWrapper<ColumnInfoHisIdEntity>()
                                // 【修正】: UPDATE 的 WHERE 条件也只使用 id 和 version
                                .eq("id", entityToUpdate.getId())
                                .eq("version", entityToUpdate.getVersion())
                );
            }
        }

        return true;
    }

    /**
     * 【修正】: 辅助方法，只根据 id 和 version 构建复合键.
     * @param entity 实体对象
     * @return 复合键字符串
     */
    private String buildCompositeKey(ColumnInfoHisIdEntity entity) {
        return entity.getId().toString() + "_" + entity.getVersion();
    }

    /**
     * 根据表ID删除字段.
     *
     * @param tableId
     *            表ID
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByTableId(UUID tableId) {
        Wrapper<ColumnInfoHisIdEntity> query =
                Wrappers.<ColumnInfoHisIdEntity>lambdaQuery().eq(ColumnInfoHisIdEntity::getTableId, tableId);
        return super.remove(query);
    }

    @Override
    public List<ColumnInfoHisIdEntity> getByTableId(UUID tableId) {
        return getByTableIds(Collections.singletonList(tableId));
    }

    @Override
    public List<ColumnInfoHisIdEntity> getByTableIds(List<UUID> tableIds) {
        if (CollectionUtils.isEmpty(tableIds)) {
            return Collections.emptyList();
        }

        Wrapper<ColumnInfoHisIdEntity> query = Wrappers.<ColumnInfoHisIdEntity>lambdaQuery()
                .in(ColumnInfoHisIdEntity::getTableId, tableIds).orderByAsc(ColumnInfoHisIdEntity::getColumnSerial);
        return super.list(query);
    }
}
