package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.WorkerRegistryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryQueryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistrySaveDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryUpdateDto;
import com.datafusion.manager.scheduler.po.WorkerRegistryEntity;
import com.datafusion.scheduler.model.Worker;

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
     * 逻辑删除 worker 注册信息.
     *
     * @param id worker 注册记录ID
     * @return 是否成功
     */
    boolean deleteWorkerRegistry(UUID id);

    /**
     * 查询可调度 worker.
     *
     * @param workerCode worker 编码
     * @return worker 注册实体
     */
    WorkerRegistryEntity getSchedulableWorkerByCode(String workerCode);

    /**
     * 按 host + port 查询可调度 worker.
     *
     * @param host host 或主机名
     * @param port 端口
     * @return worker 注册实体
     */
    WorkerRegistryEntity getSchedulableWorkerByHostAndPort(String host, Integer port);

    /**
     * 查询全部可调度 worker.
     *
     * @return worker 注册实体列表
     */
    List<WorkerRegistryEntity> listSchedulableWorkers();

    /**
     * 定位 agent 注册或心跳对应的 worker 注册记录.
     *
     * @param workerCode worker 编码
     * @param host       host
     * @param port       端口
     * @return worker 注册实体
     */
    WorkerRegistryEntity findForHeartbeat(String workerCode, String host, Integer port);

    /**
     * 保存或更新 scheduler worker 模型.
     *
     * @param worker scheduler worker
     */
    void saveOrUpdateFromWorker(Worker worker);

    /**
     * 转换为 scheduler worker 模型.
     *
     * @param entity worker 注册实体
     * @return scheduler worker
     */
    Worker toWorker(WorkerRegistryEntity entity);
}
