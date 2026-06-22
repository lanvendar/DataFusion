package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.WorkerRegistryMapper;
import com.datafusion.manager.scheduler.dto.WorkerRegistryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryQueryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistrySaveDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryUpdateDto;
import com.datafusion.manager.scheduler.po.WorkerRegistryEntity;
import com.datafusion.manager.scheduler.service.WorkerRegistryService;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.scheduler.model.Worker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-worker 注册Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Service
public class WorkerRegistryServiceImpl extends ServiceImpl<WorkerRegistryMapper, WorkerRegistryEntity>
        implements WorkerRegistryService {

    /**
     * 有效标记.
     */
    private static final int ACTIVE = 1;

    /**
     * 无效标记.
     */
    private static final int INACTIVE = 0;

    /**
     * 插件字段最大长度.
     */
    private static final int PLUGINS_MAX_LENGTH = 256;

    @Override
    public PageResponse<WorkerRegistryDto> pageWorkerRegistry(PageQuery<WorkerRegistryQueryDto> query) {
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = buildQueryWrapper(query.getOption());
        IPage<WorkerRegistryEntity> page = baseMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);

        PageResponse<WorkerRegistryDto> response = new PageResponse<>();
        response.setDataList(page.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<WorkerRegistryDto> listWorkerRegistry(WorkerRegistryQueryDto query) {
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = buildQueryWrapper(query);
        return baseMapper.selectList(wrapper).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public WorkerRegistryDto getWorkerRegistryById(UUID id) {
        WorkerRegistryEntity entity = getById(id);
        if (entity == null || Worker.STATUS_DELETED.equals(entity.getStatus())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不存在");
        }
        return toDto(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addWorkerRegistry(WorkerRegistrySaveDto dto) {
        validatePort(dto.getPort());
        validateActive(defaultActive(dto.getIsActive()));
        String plugins = normalizePlugins(dto.getPlugins());
        checkWorkerCodeUnique(dto.getWorkerCode(), null);
        checkHostPortUnique(dto.getHost(), dto.getPort(), null);

        WorkerRegistryEntity entity = new WorkerRegistryEntity();
        entity.setId(UUID.randomUUID());
        entity.setWorkerCode(dto.getWorkerCode());
        entity.setHostName(dto.getHostName());
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setStatus(Worker.STATUS_DOWN);
        entity.setZone(dto.getZone());
        entity.setPlugins(plugins);
        entity.setIsActive(defaultActive(dto.getIsActive()));
        entity.setRemark(dto.getRemark());

        fillCreateAudit(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWorkerRegistry(WorkerRegistryUpdateDto dto) {
        WorkerRegistryEntity entity = getById(dto.getId());
        if (entity == null || Worker.STATUS_DELETED.equals(entity.getStatus())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不存在");
        }

        if (StringUtils.isNotBlank(dto.getWorkerCode())) {
            entity.setWorkerCode(dto.getWorkerCode());
        }
        if (StringUtils.isNotBlank(dto.getHostName())) {
            entity.setHostName(dto.getHostName());
        }
        if (StringUtils.isNotBlank(dto.getHost())) {
            entity.setHost(dto.getHost());
        }
        if (dto.getPort() != null) {
            entity.setPort(dto.getPort());
        }
        if (dto.getZone() != null) {
            entity.setZone(dto.getZone());
        }
        if (dto.getPlugins() != null) {
            entity.setPlugins(normalizePlugins(dto.getPlugins()));
        }
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }

        validatePort(entity.getPort());
        validateActive(entity.getIsActive());
        checkWorkerCodeUnique(entity.getWorkerCode(), entity.getId());
        checkHostPortUnique(entity.getHost(), entity.getPort(), entity.getId());

        fillUpdateAudit(entity);
        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWorkerRegistry(UUID id) {
        WorkerRegistryEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不存在");
        }
        entity.setStatus(Worker.STATUS_DELETED);
        entity.setIsActive(INACTIVE);
        fillUpdateAudit(entity);
        return updateById(entity);
    }

    @Override
    public WorkerRegistryEntity getSchedulableWorkerByCode(String workerCode) {
        if (StringUtils.isBlank(workerCode)) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = buildSchedulableWrapper()
                .eq(WorkerRegistryEntity::getWorkerCode, workerCode);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public WorkerRegistryEntity getSchedulableWorkerByHostAndPort(String host, Integer port) {
        if (StringUtils.isBlank(host) || port == null) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = buildSchedulableWrapper()
                .eq(WorkerRegistryEntity::getPort, port)
                .and(item -> item.eq(WorkerRegistryEntity::getHost, host)
                        .or()
                        .eq(WorkerRegistryEntity::getHostName, host));
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public List<WorkerRegistryEntity> listSchedulableWorkers() {
        return baseMapper.selectList(buildSchedulableWrapper());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markHeartbeatTimeoutWorkersOffline(Date expireBefore) {
        if (expireBefore == null) {
            return 0;
        }
        WorkerRegistryEntity entity = new WorkerRegistryEntity();
        entity.setStatus(Worker.STATUS_DOWN);
        fillSystemUpdateAudit(entity);

        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkerRegistryEntity::getStatus, Worker.STATUS_UP)
                .and(item -> item.isNull(WorkerRegistryEntity::getLastHeartbeatTime)
                        .or()
                        .lt(WorkerRegistryEntity::getLastHeartbeatTime, expireBefore));
        return baseMapper.update(entity, wrapper);
    }

    @Override
    public WorkerRegistryEntity findForHeartbeat(String workerCode, String host, Integer port) {
        WorkerRegistryEntity entity = findByWorkerCode(workerCode);
        if (entity != null) {
            return entity;
        }
        return findByHostAndPort(host, port);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateFromWorker(Worker worker) {
        if (worker == null || StringUtils.isBlank(worker.getId())) {
            return;
        }
        WorkerRegistryEntity existing = findForHeartbeat(worker.getId(), worker.getIp(), worker.getPort());
        if (existing != null && Worker.STATUS_DELETED.equals(existing.getStatus())) {
            return;
        }
        WorkerRegistryEntity entity = toEntity(worker, existing);
        if (existing == null) {
            save(entity);
        } else {
            updateById(entity);
        }
    }

    @Override
    public Worker toWorker(WorkerRegistryEntity entity) {
        if (entity == null) {
            return null;
        }
        Worker worker = new Worker();
        worker.setId(entity.getWorkerCode());
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

    // region 私有方法

    /**
     * 构建查询条件.
     *
     * @param query 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<WorkerRegistryEntity> buildQueryWrapper(WorkerRegistryQueryDto query) {
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(WorkerRegistryEntity::getStatus, Worker.STATUS_DELETED);
        if (query != null) {
            if (StringUtils.isNotBlank(query.getWorkerCode())) {
                wrapper.like(WorkerRegistryEntity::getWorkerCode, query.getWorkerCode());
            }
            if (StringUtils.isNotBlank(query.getHostName())) {
                wrapper.like(WorkerRegistryEntity::getHostName, query.getHostName());
            }
            if (StringUtils.isNotBlank(query.getHost())) {
                wrapper.like(WorkerRegistryEntity::getHost, query.getHost());
            }
            if (query.getStatus() != null) {
                wrapper.eq(WorkerRegistryEntity::getStatus, query.getStatus());
            }
            if (StringUtils.isNotBlank(query.getZone())) {
                wrapper.eq(WorkerRegistryEntity::getZone, query.getZone());
            }
            if (query.getIsActive() != null) {
                wrapper.eq(WorkerRegistryEntity::getIsActive, query.getIsActive());
            }
        }
        wrapper.orderByDesc(WorkerRegistryEntity::getUpdateTime);
        return wrapper;
    }

    /**
     * 构建可调度查询条件.
     *
     * @return 查询条件
     */
    private LambdaQueryWrapper<WorkerRegistryEntity> buildSchedulableWrapper() {
        return new LambdaQueryWrapper<WorkerRegistryEntity>()
                .eq(WorkerRegistryEntity::getIsActive, ACTIVE)
                .eq(WorkerRegistryEntity::getStatus, Worker.STATUS_UP);
    }

    private WorkerRegistryEntity findByWorkerCode(String workerCode) {
        if (StringUtils.isBlank(workerCode)) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<WorkerRegistryEntity>()
                .eq(WorkerRegistryEntity::getWorkerCode, workerCode);
        return baseMapper.selectOne(wrapper);
    }

    private WorkerRegistryEntity findByHostAndPort(String host, Integer port) {
        if (StringUtils.isBlank(host) || port == null) {
            return null;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<WorkerRegistryEntity>()
                .eq(WorkerRegistryEntity::getHost, host)
                .eq(WorkerRegistryEntity::getPort, port);
        return baseMapper.selectOne(wrapper);
    }

    private void checkWorkerCodeUnique(String workerCode, UUID excludeId) {
        if (StringUtils.isBlank(workerCode)) {
            return;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkerRegistryEntity::getWorkerCode, workerCode);
        if (excludeId != null) {
            wrapper.ne(WorkerRegistryEntity::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker编码已存在");
        }
    }

    private void checkHostPortUnique(String host, Integer port, UUID excludeId) {
        if (StringUtils.isBlank(host) || port == null) {
            return;
        }
        LambdaQueryWrapper<WorkerRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkerRegistryEntity::getHost, host)
                .eq(WorkerRegistryEntity::getPort, port);
        if (excludeId != null) {
            wrapper.ne(WorkerRegistryEntity::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker地址和端口已存在");
        }
    }

    private WorkerRegistryEntity toEntity(Worker worker, WorkerRegistryEntity existing) {
        WorkerRegistryEntity entity = existing != null ? existing : new WorkerRegistryEntity();
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            fillSystemCreateAudit(entity);
        } else {
            fillSystemUpdateAudit(entity);
        }
        entity.setWorkerCode(worker.getId());
        entity.setHost(worker.getIp());
        entity.setHostName(worker.getHostName());
        entity.setPort(worker.getPort());
        entity.setStatus(worker.getStatus());
        entity.setPlugins(normalizePlugins(joinPlugins(worker.getPluginTypes())));
        entity.setRegisterTime(toDate(worker.getRegisterTime()));
        entity.setLastHeartbeatTime(toDate(worker.getLastHeartbeatTime()));
        entity.setLogDir(worker.getWorkerLogDir());
        if (entity.getIsActive() == null) {
            entity.setIsActive(ACTIVE);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(new Date());
        }
        if (entity.getCreator() == null) {
            entity.setCreator(HttpUtils.DEFAULT_USER_NAME);
        }
        return entity;
    }

    private WorkerRegistryDto toDto(WorkerRegistryEntity entity) {
        WorkerRegistryDto dto = new WorkerRegistryDto();
        dto.setId(entity.getId());
        dto.setWorkerCode(entity.getWorkerCode());
        dto.setHostName(entity.getHostName());
        dto.setHost(entity.getHost());
        dto.setPort(entity.getPort());
        dto.setStatus(entity.getStatus());
        dto.setZone(entity.getZone());
        dto.setPlugins(entity.getPlugins());
        dto.setRegisterTime(entity.getRegisterTime());
        dto.setLastHeartbeatTime(entity.getLastHeartbeatTime());
        dto.setWorkerLogDir(entity.getLogDir());
        dto.setIsActive(entity.getIsActive());
        dto.setRemark(entity.getRemark());
        dto.setCreator(entity.getCreator());
        dto.setUpdater(entity.getUpdater());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private void fillCreateAudit(WorkerRegistryEntity entity) {
        Date now = new Date();
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
    }

    private void fillUpdateAudit(WorkerRegistryEntity entity) {
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
    }

    private void fillSystemCreateAudit(WorkerRegistryEntity entity) {
        Date now = new Date();
        entity.setCreator(HttpUtils.DEFAULT_USER_NAME);
        entity.setUpdater(HttpUtils.DEFAULT_USER_NAME);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
    }

    private void fillSystemUpdateAudit(WorkerRegistryEntity entity) {
        entity.setUpdater(HttpUtils.DEFAULT_USER_NAME);
        entity.setUpdateTime(new Date());
    }

    private Integer defaultActive(Integer isActive) {
        return isActive == null ? ACTIVE : isActive;
    }

    private void validatePort(Integer port) {
        if (port == null || port <= 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker端口必须大于0");
        }
    }

    private void validateActive(Integer isActive) {
        if (!Integer.valueOf(ACTIVE).equals(isActive) && !Integer.valueOf(INACTIVE).equals(isActive)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker有效标记非法");
        }
    }

    private Date toDate(Long timestamp) {
        return timestamp == null ? null : new Date(timestamp);
    }

    private Long toMillis(Date date) {
        return date == null ? null : date.getTime();
    }

    private String joinPlugins(List<String> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            return null;
        }
        return plugins.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(","));
    }

    private String normalizePlugins(String plugins) {
        if (StringUtils.isBlank(plugins)) {
            return null;
        }
        String result = Arrays.stream(plugins.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(","));
        if (result.length() > PLUGINS_MAX_LENGTH) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "插件类型列表不能超过256个字符");
        }
        return result;
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
    // endregion
}
