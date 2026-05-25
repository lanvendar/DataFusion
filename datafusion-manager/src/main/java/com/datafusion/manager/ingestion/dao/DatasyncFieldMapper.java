package com.datafusion.manager.ingestion.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.ingestion.po.IngestionDatasyncFieldEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据同步任务-从表Mapper(字段映射).
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Mapper
public interface DatasyncFieldMapper extends BaseMapper<IngestionDatasyncFieldEntity> {

    /**
     * 查询指定taskId的所有字段.
     *
     * @param taskId 任务ID
     * @return 字段列表
     */
    List<IngestionDatasyncFieldEntity> selectByTaskId(@Param("taskId") java.util.UUID taskId);

    /**
     * 删除指定taskId的所有字段.
     *
     * @param taskId 任务ID
     * @return 删除行数
     */
    int deleteByTaskId(@Param("taskId") java.util.UUID taskId);

    /**
     * 批量插入字段.
     *
     * @param fields 字段列表
     * @return 插入行数
     */
    int batchInsert(@Param("fields") List<IngestionDatasyncFieldEntity> fields);
}