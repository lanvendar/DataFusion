package com.datafusion.manager.system.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.manager.system.dto.TaskTypeConfigDto;
import com.datafusion.manager.system.dto.TaskTypeConfigQueryDto;
import com.datafusion.manager.system.po.TaskTypeConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 系统-任务类型配置Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Mapper
public interface TaskTypeConfigMapper extends BaseMapper<TaskTypeConfigEntity> {

    /**
     * 分页查询任务类型配置.
     *
     * @param page     分页对象
     * @param query    查询条件
     * @param tenantId 租户ID
     * @return 任务类型配置分页
     */
    Page<TaskTypeConfigDto> pageTaskTypeConfig(Page<TaskTypeConfigDto> page,
                                               @Param("query") TaskTypeConfigQueryDto query,
                                               @Param("tenantId") UUID tenantId);

    /**
     * 查询任务类型配置列表.
     *
     * @param query    查询条件
     * @param tenantId 租户ID
     * @return 任务类型配置列表
     */
    List<TaskTypeConfigDto> listTaskTypeConfig(@Param("query") TaskTypeConfigQueryDto query,
                                               @Param("tenantId") UUID tenantId);

    /**
     * 根据ID查询任务类型配置.
     *
     * @param id       任务类型配置ID
     * @param tenantId 租户ID
     * @return 任务类型配置
     */
    TaskTypeConfigDto getTaskTypeConfigById(@Param("id") UUID id,
                                            @Param("tenantId") UUID tenantId);
}
