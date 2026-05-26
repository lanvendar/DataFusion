package com.datafusion.manager.ingestion.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.manager.ingestion.dto.DatasyncTaskDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskQueryDto;
import com.datafusion.manager.ingestion.po.IngestionDatasyncTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据同步任务-主表Mapper.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Mapper
public interface DatasyncTaskMapper extends BaseMapper<IngestionDatasyncTaskEntity> {

    /**
     * 分页查询数据同步任务.
     *
     * @param query 查询参数
     * @return 任务列表
     */
    List<DatasyncTaskDto> pageTaskList(@Param("query") PageQuery<DatasyncTaskQueryDto> query);

    /**
     * 分页查询总数.
     *
     * @param query 查询参数
     * @return 总数
     */
    Integer pageTaskCount(@Param("query") PageQuery<DatasyncTaskQueryDto> query);

    /**
     * 列表查询数据同步任务(不分页).
     *
     * @param query 查询参数
     * @return 任务列表
     */
    List<DatasyncTaskDto> listTask(@Param("query") DatasyncTaskQueryDto query);

    /**
     * 查询当天最大code序号.
     *
     * @param prefix code前缀
     * @return 最大code
     */
    String selectMaxTaskCodeSeq(@Param("prefix") String prefix);
}