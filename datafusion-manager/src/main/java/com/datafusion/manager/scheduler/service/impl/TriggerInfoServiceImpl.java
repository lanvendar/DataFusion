package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.TriggerInfoMapper;
import com.datafusion.manager.scheduler.dto.TriggerInfoDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoQueryDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoSaveDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoUpdateDto;
import com.datafusion.manager.scheduler.po.TriggerInfoEntity;
import com.datafusion.manager.scheduler.service.FlowInfoService;
import com.datafusion.manager.scheduler.service.TriggerInfoService;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-触发器信息Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class TriggerInfoServiceImpl extends ServiceImpl<TriggerInfoMapper, TriggerInfoEntity>
        implements TriggerInfoService {

    /**
     * 流程信息Service, 用于删除时的引用检查.
     */
    private final FlowInfoService flowInfoService;

    @Override
    public TriggerInfoEntity getByTriggerId(UUID triggerId) {
        return baseMapper.getByTriggerId(triggerId);
    }

    @Override
    public PageResponse<TriggerInfoDto> pageTriggerInfo(PageQuery<TriggerInfoQueryDto> query) {
        LambdaQueryWrapper<TriggerInfoEntity> wrapper = buildQueryWrapper(query.getOption());
        IPage<TriggerInfoEntity> page = baseMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);

        PageResponse<TriggerInfoDto> response = new PageResponse<>();
        response.setDataList(page.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<TriggerInfoDto> listTriggerInfo(TriggerInfoQueryDto query) {
        LambdaQueryWrapper<TriggerInfoEntity> wrapper = buildQueryWrapper(query);
        return baseMapper.selectList(wrapper).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public TriggerInfoDto getTriggerInfoById(UUID id) {
        TriggerInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "触发器不存在");
        }
        return toDto(entity);
    }

    @Override
    public UUID addTriggerInfo(TriggerInfoSaveDto dto) {
        checkNameUnique(dto.getName(), null);
        validateTypeCronInterval(dto.getType(), dto.getCron(), dto.getInterval());

        TriggerInfoEntity entity = new TriggerInfoEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(dto.getName());
        entity.setType(typeNameToOrdinal(dto.getType()));
        entity.setPolicy(policyNameToOrdinal(dto.getPolicy()));
        entity.setCron(dto.getCron());
        entity.setInterval(dto.getInterval());

        Date now = new Date();
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateTriggerInfo(TriggerInfoUpdateDto dto) {
        TriggerInfoEntity entity = getById(dto.getId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "触发器不存在");
        }

        checkNameUnique(dto.getName(), dto.getId());

        // 合并非空字段
        if (StringUtils.isNotBlank(dto.getName())) {
            entity.setName(dto.getName());
        }
        if (StringUtils.isNotBlank(dto.getType())) {
            entity.setType(typeNameToOrdinal(dto.getType()));
        }
        if (StringUtils.isNotBlank(dto.getPolicy())) {
            entity.setPolicy(policyNameToOrdinal(dto.getPolicy()));
        }
        if (dto.getCron() != null) {
            entity.setCron(dto.getCron());
        }
        if (dto.getInterval() != null) {
            entity.setInterval(dto.getInterval());
        }

        // 对合并后的结果执行一致性校验
        validateTypeCronInterval(typeOrdinalToName(entity.getType()), entity.getCron(), entity.getInterval());

        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return updateById(entity);
    }

    @Override
    public boolean deleteTriggerInfo(UUID id) {
        // 引用检查: trigger_info.id = flow_info.id (共享主键)
        if (flowInfoService.getFlowInfo(id) != null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "该触发器已关联流程, 无法删除");
        }
        return removeById(id);
    }

    // region 私有方法

    /**
     * 构建查询条件.
     *
     * @param query 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<TriggerInfoEntity> buildQueryWrapper(TriggerInfoQueryDto query) {
        LambdaQueryWrapper<TriggerInfoEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getName())) {
                wrapper.like(TriggerInfoEntity::getName, query.getName());
            }
            if (StringUtils.isNotBlank(query.getType())) {
                wrapper.eq(TriggerInfoEntity::getType, typeNameToOrdinal(query.getType()));
            }
            if (StringUtils.isNotBlank(query.getPolicy())) {
                wrapper.eq(TriggerInfoEntity::getPolicy, policyNameToOrdinal(query.getPolicy()));
            }
        }
        wrapper.orderByDesc(TriggerInfoEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 校验name唯一性.
     *
     * @param name      触发器名称
     * @param excludeId 排除的ID(修改时排除自身)
     */
    private void checkNameUnique(String name, UUID excludeId) {
        if (StringUtils.isBlank(name)) {
            return;
        }
        LambdaQueryWrapper<TriggerInfoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TriggerInfoEntity::getName, name);
        if (excludeId != null) {
            wrapper.ne(TriggerInfoEntity::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "触发器名称已存在");
        }
    }

    /**
     * 校验type与cron/interval的一致性.
     *
     * @param typeName 触发器类型名称(CRON/INTERVAL)
     * @param cron     cron表达式
     * @param interval 周期间隔
     */
    private void validateTypeCronInterval(String typeName, String cron, Integer interval) {
        if (TriggerTypeEnum.CRON.name().equals(typeName) && StringUtils.isBlank(cron)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "CRON类型触发器必须填写cron表达式");
        }
        if (TriggerTypeEnum.INTERVAL.name().equals(typeName)) {
            if (interval == null || interval <= 0) {
                throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "INTERVAL类型触发器必须填写大于0的周期间隔");
            }
        }
    }

    /**
     * 触发器类型: 枚举名称 → ordinal字符串.
     *
     * @param typeName 枚举名称
     * @return ordinal字符串
     */
    private String typeNameToOrdinal(String typeName) {
        return String.valueOf(TriggerTypeEnum.valueOf(typeName).ordinal());
    }

    /**
     * 触发器类型: ordinal字符串 → 枚举名称.
     *
     * @param ordinal ordinal字符串
     * @return 枚举名称
     */
    private String typeOrdinalToName(String ordinal) {
        if (ordinal == null) {
            return null;
        }
        return TriggerTypeEnum.valueOf(Integer.parseInt(ordinal)).name();
    }

    /**
     * 调度策略: 枚举名称 → ordinal字符串.
     *
     * @param policyName 枚举名称
     * @return ordinal字符串
     */
    private String policyNameToOrdinal(String policyName) {
        return String.valueOf(TriggerPolicyEnum.valueOf(policyName).ordinal());
    }

    /**
     * 调度策略: ordinal字符串 → 枚举名称.
     *
     * @param ordinal ordinal字符串
     * @return 枚举名称
     */
    private String policyOrdinalToName(String ordinal) {
        if (ordinal == null) {
            return null;
        }
        return TriggerPolicyEnum.valueOf(Integer.parseInt(ordinal)).name();
    }

    /**
     * Entity转Dto.
     *
     * @param entity 触发器实体
     * @return 触发器Dto
     */
    private TriggerInfoDto toDto(TriggerInfoEntity entity) {
        TriggerInfoDto dto = new TriggerInfoDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(typeOrdinalToName(entity.getType()));
        dto.setPolicy(policyOrdinalToName(entity.getPolicy()));
        dto.setCron(entity.getCron());
        dto.setInterval(entity.getInterval());
        dto.setCreator(entity.getCreator());
        dto.setUpdater(entity.getUpdater());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
    // endregion
}
