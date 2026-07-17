package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.WorkerRegistryActiveDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryQueryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistrySaveDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryUpdateDto;
import com.datafusion.manager.scheduler.po.WorkerRegistryEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-worker 注册Service.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
public interface WorkerRegistryService extends IService<WorkerRegistryEntity> {

    /**
     * 分页查询 worker 注册信息.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<WorkerRegistryDto> pageWorkerRegistry(PageQuery<WorkerRegistryQueryDto> query);

    /**
     * 查询 worker 注册列表.
     *
     * @param query 查询条件
     * @return worker 注册列表
     */
    List<WorkerRegistryDto> listWorkerRegistry(WorkerRegistryQueryDto query);

    /**
     * 根据ID查询 worker 注册详情.
     *
     * @param id worker 注册记录ID
     * @return worker 注册详情
     */
    WorkerRegistryDto getWorkerRegistryById(UUID id);

    /**
     * 新增 worker 注册信息.
     *
     * @param dto 新增参数
     * @return worker 注册记录ID
     */
    UUID addWorkerRegistry(WorkerRegistrySaveDto dto);

    /**
     * 修改 worker 注册信息.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateWorkerRegistry(WorkerRegistryUpdateDto dto);

    /**
     * 启用或禁用 worker 调度.
     *
     * @param dto 启停参数
     * @return 是否成功
     */
    boolean updateWorkerActive(WorkerRegistryActiveDto dto);

    /**
     * 删除 worker 注册信息.
     *
     * @param id worker 注册记录ID
     * @return 是否成功
     */
    boolean deleteWorkerRegistry(UUID id);
}
