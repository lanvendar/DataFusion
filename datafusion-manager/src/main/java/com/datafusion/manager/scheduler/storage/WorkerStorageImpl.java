package com.datafusion.manager.scheduler.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.manager.scheduler.dao.WorkerRegistryMapper;
import com.datafusion.manager.scheduler.po.WorkerRegistryEntity;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.storage.WorkerStorage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * worker 存储实现, 适配 scheduler WorkerStorage 接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class WorkerStorageImpl implements WorkerStorage {

    /**
     * 有效标记.
     */
    private static final int ACTIVE = 1;

    /**
     * worker 注册Mapper.
     */
    private final WorkerRegistryMapper workerRegistryMapper;

    @Override
    public Worker getWorker(String workerId) {
        WorkerRegistryEntity entity = getSchedulableWorkerById(workerId);
        return toWorker(entity);
    }

    @Override
    public Worker getWorker(String hostName, int port) {
        WorkerRegistryEntity entity = getSchedulableWorkerByHostAndPort(hostName, port);
        return toWorker(entity);
    }

    @Override
    public List<Worker> getWorkers() {
        return workerRegistryMapper.selectList(buildSchedulableWrapper()).stream()
                .map(this::toWorker)
                .collect(Collectors.toList());
    }

    @Override
    public void updateWorker(Worker worker) {
        register(worker);
    }

    @Override
    public Worker register(Worker worker) {
        if (worker == null || StringUtils.isBlank(worker.getWorkerCode())) {
            return null;
        }
        WorkerRegistryEntity existing = getWorkerById(worker.getId());
        if (existing == null) {
            existing = findByWorkerCode(worker.getWorkerCode());
        }
        if (existing == null) {
            existing = findByHostAndPort(worker.getIp(), worker.getPort());
        }
        if (existing != null && Worker.STATUS_DELETED.equals(existing.getStatus())) {
            return null;
        }
        WorkerRegistryEntity entity = toEntity(worker, existing);
        if (existing == null) {
            workerRegistryMapper.insert(entity);
        } else {
            workerRegistryMapper.updateById(entity);
        }
        return toWorker(entity);
    }

    @Override
    public Worker heartbeat(String workerId, Long lastHeartbeatTime) {
        WorkerRegistryEntity entity = getWorkerById(workerId);
        if (entity == null || Worker.STATUS_DELETED.equals(entity.getStatus())) {
            return null;
        }
        entity.setStatus(Worker.STATUS_UP);
        entity.setLastHeartbeatTime(toDate(lastHeartbeatTime, new Date()));
        fillSystemUpdateAudit(entity);
        workerRegistryMapper.updateById(entity);
        return toWorker(entity);
    }

    @Override
    public Worker offline(String workerId) {
        WorkerRegistryEntity entity = getWorkerById(workerId);
        if (entity == null || Worker.STATUS_DELETED.equals(entity.getStatus())) {
            return null;
        }
        entity.setStatus(Worker.STATUS_DOWN);
        fillSystemUpdateAudit(entity);
        workerRegistryMapper.updateById(entity);
        return toWorker(entity);
    }

    @Override
    public int timeoutOffline(Long timeoutMs) {
        if (timeoutMs == null || timeoutMs < 0) {
            return 0;
        }
        WorkerRegistryEntity entity = new WorkerRegistryEntity();
        entity.setStatus(Worker.STATUS_DOWN);
        fillSystemUpdateAudit(entity);
        Date expireBefore = new Date(System.currentTimeMillis() - timeoutMs);
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkerRegistryEntity::getStatus, Worker.STATUS_UP)
                .and(item -> item.isNull(WorkerRegistryEntity::getLastHeartbeatTime)
                        .or()
                        .lt(WorkerRegistryEntity::getLastHeartbeatTime, expireBefore));
        return workerRegistryMapper.update(entity, wrapper);
    }

    @Override
    public List<TaskRequest> getTaskInsByWorkerId(String workerId) {
        return Collections.emptyList();
    }

    private WorkerRegistryEntity getSchedulableWorkerById(String workerId) {
        UUID id = parseWorkerId(workerId);
        if (id == null) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = buildSchedulableWrapper()
                .eq(WorkerRegistryEntity::getId, id);
        return workerRegistryMapper.selectOne(wrapper);
    }

    private WorkerRegistryEntity getSchedulableWorkerByHostAndPort(String host, Integer port) {
        if (StringUtils.isBlank(host) || port == null) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = buildSchedulableWrapper()
                .eq(WorkerRegistryEntity::getPort, port)
                .and(item -> item.eq(WorkerRegistryEntity::getHost, host)
                        .or()
                        .eq(WorkerRegistryEntity::getHostName, host));
        return workerRegistryMapper.selectOne(wrapper);
    }

    private WorkerRegistryEntity getWorkerById(String workerId) {
        UUID id = parseWorkerId(workerId);
        return id == null ? null : workerRegistryMapper.selectById(id);
    }

    private WorkerRegistryEntity findByWorkerCode(String workerCode) {
        if (StringUtils.isBlank(workerCode)) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<WorkerRegistryEntity>()
                .eq(WorkerRegistryEntity::getWorkerCode, workerCode);
        return workerRegistryMapper.selectOne(wrapper);
    }

    private WorkerRegistryEntity findByHostAndPort(String host, Integer port) {
        if (StringUtils.isBlank(host) || port == null) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<WorkerRegistryEntity>()
                .eq(WorkerRegistryEntity::getHost, host)
                .eq(WorkerRegistryEntity::getPort, port);
        return workerRegistryMapper.selectOne(wrapper);
    }

    private LambdaQueryWrapper<WorkerRegistryEntity> buildSchedulableWrapper() {
        return new LambdaQueryWrapper<WorkerRegistryEntity>()
                .eq(WorkerRegistryEntity::getIsActive, ACTIVE)
                .eq(WorkerRegistryEntity::getStatus, Worker.STATUS_UP);
    }

    private WorkerRegistryEntity toEntity(Worker worker, WorkerRegistryEntity existing) {
        WorkerRegistryEntity entity = existing != null ? existing : new WorkerRegistryEntity();
        Date now = new Date();
        if (entity.getId() == null) {
            UUID workerId = parseWorkerId(worker.getId());
            entity.setId(workerId == null ? UUID.randomUUID() : workerId);
            fillSystemCreateAudit(entity, now);
        } else {
            fillSystemUpdateAudit(entity, now);
        }
        entity.setWorkerCode(worker.getWorkerCode());
        entity.setHost(worker.getIp());
        entity.setHostName(worker.getHostName());
        entity.setPort(worker.getPort());
        entity.setStatus(worker.getStatus() == null ? Worker.STATUS_UP : worker.getStatus());
        entity.setPlugins(joinPlugins(worker.getPluginTypes()));
        if (entity.getRegisterTime() == null) {
            entity.setRegisterTime(toDate(worker.getRegisterTime(), now));
        }
        entity.setLastHeartbeatTime(toDate(worker.getLastHeartbeatTime(), now));
        if (StringUtils.isBlank(entity.getLogDir()) && StringUtils.isNotBlank(worker.getWorkerLogDir())) {
            entity.setLogDir(worker.getWorkerLogDir());
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(ACTIVE);
        }
        return entity;
    }

    private Worker toWorker(WorkerRegistryEntity entity) {
        if (entity == null) {
            return null;
        }
        Worker worker = new Worker();
        worker.setId(entity.getId() == null ? null : entity.getId().toString());
        worker.setWorkerCode(entity.getWorkerCode());
        worker.setIp(entity.getHost());
        worker.setHostName(entity.getHostName());
        worker.setPort(entity.getPort());
        worker.setStatus(entity.getStatus());
        worker.setPluginTypes(splitPlugins(entity.getPlugins()));
        worker.setRegisterTime(toMillis(entity.getRegisterTime()));
        worker.setLastHeartbeatTime(toMillis(entity.getLastHeartbeatTime()));
        worker.setWorkerLogDir(entity.getLogDir());
        worker.setUpdateTime(toMillis(entity.getUpdateTime()));
        return worker;
    }

    private List<String> splitPlugins(String plugins) {
        if (StringUtils.isBlank(plugins)) {
            return Collections.emptyList();
        }
        return Arrays.stream(plugins.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private String joinPlugins(List<String> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            return null;
        }
        return plugins.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(","));
    }

    private UUID parseWorkerId(String workerId) {
        if (StringUtils.isBlank(workerId)) {
            return null;
        }
        try {
            return UUID.fromString(workerId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void fillSystemCreateAudit(WorkerRegistryEntity entity, Date now) {
        entity.setCreator(HttpUtils.DEFAULT_USER_NAME);
        entity.setUpdater(HttpUtils.DEFAULT_USER_NAME);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
    }

    private void fillSystemUpdateAudit(WorkerRegistryEntity entity, Date now) {
        entity.setUpdater(HttpUtils.DEFAULT_USER_NAME);
        entity.setUpdateTime(now);
    }

    private void fillSystemUpdateAudit(WorkerRegistryEntity entity) {
        fillSystemUpdateAudit(entity, new Date());
    }

    private Date toDate(Long timestamp) {
        return timestamp == null ? null : new Date(timestamp);
    }

    private Date toDate(Long timestamp, Date defaultDate) {
        return timestamp == null ? defaultDate : new Date(timestamp);
    }

    private Long toMillis(Date date) {
        return date == null ? null : date.getTime();
    }
}
