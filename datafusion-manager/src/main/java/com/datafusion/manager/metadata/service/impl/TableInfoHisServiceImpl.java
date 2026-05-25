package com.datafusion.manager.metadata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.manager.metadata.dao.TableInfoHisMapper;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import com.datafusion.manager.metadata.po.TableInfoHisIdEntity;
import com.datafusion.manager.metadata.service.TableInfoHisService;
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
 * 元数据-表历史信息服务.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
@Slf4j
@Service
public class TableInfoHisServiceImpl extends ServiceImpl<TableInfoHisMapper, TableInfoHisIdEntity>
        implements TableInfoHisService {

    @Override
    public boolean customSaveOrUpdateBatch(List<TableInfoEntity> tableInfoSnapshotList, String version) {
        // 首先将TableInfoEntity 转成 TableInfoHisEntity
        List<TableInfoHisIdEntity> hisList = tableInfoSnapshotList.stream().map(dto -> {
            TableInfoHisIdEntity vo = new TableInfoHisIdEntity();
            BeanUtils.copyProperties(dto, vo);
            // 赋值版本信息
            vo.setVersion(version);
            // 赋值uuid
            // vo.setId(IdGenerator.createTableId(vo.getDatasourceId(), vo.getTableName()));
            return vo;
        }).collect(Collectors.toList());
        return this.saveOrUpdateBatch(hisList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // 保证整个操作的原子性
    public boolean saveOrUpdateBatch(List<TableInfoHisIdEntity> entityList) {
        if (CollectionUtils.isEmpty(entityList)) {
            return true; // 如果列表为空，直接返回成功
        }

        // --- 步骤 1: 构造查询条件，一次性找出所有已存在的记录 ---

        // 使用 WHERE (id = ? AND version = ?) OR (id = ? AND version = ?) ... 的方式
        QueryWrapper<TableInfoHisIdEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> {
            for (TableInfoHisIdEntity entity : entityList) {
                // 为每个实体的主键组合创建一个 OR 条件
                wrapper.or(w -> w.eq("id", entity.getId()).eq("version", entity.getVersion()));
            }
        });

        // 执行一次查询，获取所有已存在的记录
        List<TableInfoHisIdEntity> existingEntities = this.list(queryWrapper);

        // 将已存在的记录转换为一个易于查找的 Map，以 "id_version" 作为 Key
        Map<String, TableInfoHisIdEntity> existingMap = existingEntities.stream()
                .collect(Collectors.toMap(
                        // 使用 UUID 和 String 组合成一个唯一的复合键
                        entity -> entity.getId().toString() + "_" + entity.getVersion(),
                        entity -> entity
                ));

        // --- 步骤 2: 将传入的列表分组为 toInsertList 和 toUpdateList ---

        List<TableInfoHisIdEntity> toInsertList = new ArrayList<>();
        List<TableInfoHisIdEntity> toUpdateList = new ArrayList<>();

        for (TableInfoHisIdEntity entity : entityList) {
            String compositeKey = entity.getId().toString() + "_" + entity.getVersion();
            if (existingMap.containsKey(compositeKey)) {
                // 如果数据库中已存在，则加入更新列表
                toUpdateList.add(entity);
            } else {
                // 如果不存在，则加入新增列表
                toInsertList.add(entity);
            }
        }

        // --- 步骤 3: 执行批量操作 ---

        // A. 批量插入新记录
        if (!toInsertList.isEmpty()) {
            // saveBatch 是 MyBatis-Plus 提供的
            this.saveBatch(toInsertList);
        }

        // B. 批量更新已存在的记录
        if (!toUpdateList.isEmpty()) {
            // 对于批量更新，最直接的方式是在事务中循环更新。
            // 如果需要极致性能，可以自定义 Mapper XML 实现 case-when 更新。
            for (TableInfoHisIdEntity entityToUpdate : toUpdateList) {
                this.update(
                        entityToUpdate, // entityToUpdate 中包含了要更新的字段值
                        new QueryWrapper<TableInfoHisIdEntity>()
                                .eq("id", entityToUpdate.getId())
                                .eq("version", entityToUpdate.getVersion())
                );
            }
        }

        return true;
    }

    @Override
    public List<TableInfoHisIdEntity> getByTableId(UUID tableId) {
        return getByTableIds(Collections.singletonList(tableId));
    }

    @Override
    public List<TableInfoHisIdEntity> getByTableIds(List<UUID> tableIds) {
        if (CollectionUtils.isEmpty(tableIds)) {
            return Collections.emptyList();
        }

        Wrapper<TableInfoHisIdEntity> query = Wrappers.<TableInfoHisIdEntity>lambdaQuery()
                .in(TableInfoHisIdEntity::getId, tableIds).orderByAsc(TableInfoHisIdEntity::getCreateTime);
        return super.list(query);
    }

    @Override
    public boolean deleteByTableId(UUID tableId) {
        Wrapper<TableInfoHisIdEntity> query =
                Wrappers.<TableInfoHisIdEntity>lambdaQuery().eq(TableInfoHisIdEntity::getId, tableId);
        return super.remove(query);
    }
}
