package com.datafusion.manager.development.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskQueryDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskSaveDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskUpdateDto;
import com.datafusion.manager.development.po.DevelopScriptSqlTaskEntity;

import java.util.List;
import java.util.UUID;

/**
 * 数据开发-SQL脚本任务服务.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
public interface DevelopScriptSqlTaskService extends IService<DevelopScriptSqlTaskEntity> {

    /**
     * 分页查询SQL脚本任务.
     *
     * @param query 分页条件
     * @return 分页结果
     */
    PageResponse<DevelopScriptSqlTaskDto> pageTask(PageQuery<DevelopScriptSqlTaskQueryDto> query);

    /**
     * 列表查询SQL脚本任务（不分页）.
     *
     * @param query 查询条件
     * @return 列表
     */
    List<DevelopScriptSqlTaskDto> listTask(DevelopScriptSqlTaskQueryDto query);

    /**
     * 按主键查询详情（仅未软删）.
     *
     * @param id 主键
     * @return 详情
     */
    DevelopScriptSqlTaskDto getTaskById(UUID id);

    /**
     * 新增SQL脚本任务.
     *
     * @param dto 入参
     * @return 新记录主键
     */
    UUID addTask(DevelopScriptSqlTaskSaveDto dto);

    /**
     * 修改SQL脚本任务（已软删不可改）.
     *
     * @param dto 入参
     * @return 是否成功
     */
    boolean updateTask(DevelopScriptSqlTaskUpdateDto dto);

    /**
     * 软删除SQL脚本任务.
     *
     * @param id 主键
     * @return 是否成功
     */
    boolean softDeleteTask(UUID id);

    /**
     * 发布SQL脚本任务（幂等：已发布则刷新发布时间）.
     *
     * @param id 主键
     * @return 是否成功
     */
    boolean publishTask(UUID id);
}
