package com.datafusion.manager.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.system.dao.PluginConfigMapper;
import com.datafusion.manager.system.dto.PluginConfigDto;
import com.datafusion.manager.system.dto.PluginConfigQueryDto;
import com.datafusion.manager.system.dto.PluginConfigSaveDto;
import com.datafusion.manager.system.dto.PluginConfigUpdateDto;
import com.datafusion.manager.system.po.PluginConfigEntity;
import com.datafusion.manager.system.service.PluginConfigService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 系统-插件配置Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class PluginConfigServiceImpl extends ServiceImpl<PluginConfigMapper, PluginConfigEntity>
        implements PluginConfigService {

    /**
     * 正常状态.
     */
    private static final Short IS_DEL_NORMAL = 0;

    /**
     * 删除状态.
     */
    private static final Short IS_DEL_DELETED = 1;

    /**
     * 默认复制名称最大重试次数.
     */
    private static final int COPY_NAME_MAX_RETRY_COUNT = 10;

    /**
     * 临时默认租户ID.
     */
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public PageResponse<PluginConfigDto> pagePluginConfig(PageQuery<PluginConfigQueryDto> query) {
        LambdaQueryWrapper<PluginConfigEntity> wrapper = buildQueryWrapper(query.getOption());
        IPage<PluginConfigEntity> page = baseMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);

        PageResponse<PluginConfigDto> response = new PageResponse<>();
        response.setDataList(page.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<PluginConfigDto> listPluginConfig(PluginConfigQueryDto query) {
        LambdaQueryWrapper<PluginConfigEntity> wrapper = buildQueryWrapper(query);
        return baseMapper.selectList(wrapper).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public PluginConfigDto getPluginConfigById(UUID id) {
        return toDto(getActivePluginConfig(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addPluginConfig(PluginConfigSaveDto dto) {
        UUID tenantId = DEFAULT_TENANT_ID;
        checkPluginNameUnique(tenantId, dto.getPluginType(), dto.getRunMode(), dto.getPluginName(), null);

        PluginConfigEntity entity = new PluginConfigEntity();
        entity.setPluginName(dto.getPluginName());
        entity.setPluginType(dto.getPluginType());
        entity.setRunMode(dto.getRunMode());
        entity.setDescription(dto.getDescription());
        entity.setPluginParam(dto.getPluginParam());
        entity.setIsTemplate(Boolean.FALSE);
        entity.setIsDel(IS_DEL_NORMAL);
        entity.setTenantId(tenantId);
        entity.setId(generatePluginConfigId(
                entity.getPluginName(), entity.getPluginType(), entity.getRunMode(), tenantId));
        fillCreateAudit(entity);

        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID copyPluginConfig(PluginConfigSaveDto dto) {
        UUID tenantId = DEFAULT_TENANT_ID;
        String pluginName = generateCopyPluginName(
                tenantId, dto.getPluginType(), dto.getRunMode(), dto.getPluginName());
        checkPluginNameUnique(tenantId, dto.getPluginType(), dto.getRunMode(), pluginName, null);

        PluginConfigEntity entity = new PluginConfigEntity();
        entity.setPluginName(pluginName);
        entity.setPluginType(dto.getPluginType());
        entity.setRunMode(dto.getRunMode());
        entity.setDescription(dto.getDescription());
        entity.setPluginParam(dto.getPluginParam());
        entity.setIsTemplate(Boolean.FALSE);
        entity.setIsDel(IS_DEL_NORMAL);
        entity.setTenantId(tenantId);
        entity.setId(generatePluginConfigId(
                entity.getPluginName(), entity.getPluginType(), entity.getRunMode(), tenantId));
        fillCreateAudit(entity);

        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePluginConfig(PluginConfigUpdateDto dto) {
        PluginConfigEntity entity = getActivePluginConfig(dto.getId());

        String pluginType = StringUtils.defaultIfBlank(dto.getPluginType(), entity.getPluginType());
        String pluginName = StringUtils.defaultIfBlank(dto.getPluginName(), entity.getPluginName());
        String runMode = StringUtils.defaultIfBlank(dto.getRunMode(), entity.getRunMode());
        if (!StringUtils.equals(pluginName, entity.getPluginName())
                || !StringUtils.equals(pluginType, entity.getPluginType())
                || !StringUtils.equals(runMode, entity.getRunMode())) {
            checkPluginNameUnique(entity.getTenantId(), pluginType, runMode, pluginName, entity.getId());
        }

        entity.setPluginName(pluginName);
        entity.setPluginType(pluginType);
        entity.setRunMode(runMode);
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getPluginParam() != null) {
            entity.setPluginParam(dto.getPluginParam());
        }
        fillUpdateAudit(entity);

        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePluginConfig(UUID id) {
        PluginConfigEntity entity = getActivePluginConfig(id);
        entity.setIsDel(IS_DEL_DELETED);
        fillUpdateAudit(entity);
        return updateById(entity);
    }

    @Override
    public PluginConfigEntity getPluginConfigByTypeAndMode(String pluginType, String runMode) {
        LambdaQueryWrapper<PluginConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginConfigEntity::getTenantId, DEFAULT_TENANT_ID);
        wrapper.eq(PluginConfigEntity::getPluginType, pluginType);
        wrapper.eq(PluginConfigEntity::getRunMode, runMode);
        wrapper.eq(PluginConfigEntity::getIsDel, IS_DEL_NORMAL);
        wrapper.orderByDesc(PluginConfigEntity::getCreateTime);
        wrapper.last("limit 1");
        return baseMapper.selectOne(wrapper);
    }

    /**
     * 构建查询条件.
     *
     * @param query 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<PluginConfigEntity> buildQueryWrapper(PluginConfigQueryDto query) {
        LambdaQueryWrapper<PluginConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginConfigEntity::getTenantId, DEFAULT_TENANT_ID);
        if (query != null) {
            if (StringUtils.isNotBlank(query.getPluginName())) {
                wrapper.like(PluginConfigEntity::getPluginName, query.getPluginName());
            }
            if (StringUtils.isNotBlank(query.getPluginType())) {
                wrapper.eq(PluginConfigEntity::getPluginType, query.getPluginType());
            }
            if (StringUtils.isNotBlank(query.getRunMode())) {
                wrapper.eq(PluginConfigEntity::getRunMode, query.getRunMode());
            }
            if (query.getIsTemplate() != null) {
                wrapper.eq(PluginConfigEntity::getIsTemplate, query.getIsTemplate());
            }
            wrapper.eq(PluginConfigEntity::getIsDel,
                    query.getIsDel() == null ? IS_DEL_NORMAL : query.getIsDel());
        } else {
            wrapper.eq(PluginConfigEntity::getIsDel, IS_DEL_NORMAL);
        }
        wrapper.orderByDesc(PluginConfigEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 查询当前租户下的正常插件配置.
     *
     * @param id 插件配置ID
     * @return 插件配置实体
     */
    private PluginConfigEntity getActivePluginConfig(UUID id) {
        LambdaQueryWrapper<PluginConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginConfigEntity::getId, id);
        wrapper.eq(PluginConfigEntity::getTenantId, DEFAULT_TENANT_ID);
        wrapper.eq(PluginConfigEntity::getIsDel, IS_DEL_NORMAL);
        PluginConfigEntity entity = baseMapper.selectOne(wrapper);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "插件配置不存在");
        }
        return entity;
    }

    /**
     * 校验插件名称唯一性.
     *
     * @param tenantId   租户ID
     * @param pluginType 插件类型
     * @param runMode    运行模式
     * @param pluginName 插件名称
     * @param excludeId  排除的ID
     */
    private void checkPluginNameUnique(
            UUID tenantId, String pluginType, String runMode, String pluginName, UUID excludeId) {
        LambdaQueryWrapper<PluginConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginConfigEntity::getTenantId, tenantId);
        wrapper.eq(PluginConfigEntity::getPluginType, pluginType);
        wrapper.eq(PluginConfigEntity::getRunMode, runMode);
        wrapper.eq(PluginConfigEntity::getPluginName, pluginName);
        wrapper.eq(PluginConfigEntity::getIsDel, IS_DEL_NORMAL);
        if (excludeId != null) {
            wrapper.ne(PluginConfigEntity::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "插件名称已存在");
        }
    }

    /**
     * 生成复制插件名称.
     *
     * @param tenantId         租户ID
     * @param pluginType       插件类型
     * @param runMode          运行模式
     * @param sourcePluginName 源插件名称
     * @return 新插件名称
     */
    private String generateCopyPluginName(UUID tenantId, String pluginType, String runMode, String sourcePluginName) {
        for (int index = 0; index < COPY_NAME_MAX_RETRY_COUNT; index++) {
            String pluginName = sourcePluginName + "_" + randomFourDigitCode();
            if (!isPluginNameExists(tenantId, pluginType, runMode, pluginName)) {
                return pluginName;
            }
        }
        throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "插件名称已存在");
    }

    /**
     * 判断插件名称是否存在.
     *
     * @param tenantId   租户ID
     * @param pluginType 插件类型
     * @param runMode    运行模式
     * @param pluginName 插件名称
     * @return 是否存在
     */
    private boolean isPluginNameExists(UUID tenantId, String pluginType, String runMode, String pluginName) {
        LambdaQueryWrapper<PluginConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginConfigEntity::getTenantId, tenantId);
        wrapper.eq(PluginConfigEntity::getPluginType, pluginType);
        wrapper.eq(PluginConfigEntity::getRunMode, runMode);
        wrapper.eq(PluginConfigEntity::getPluginName, pluginName);
        wrapper.eq(PluginConfigEntity::getIsDel, IS_DEL_NORMAL);
        return baseMapper.selectCount(wrapper) > 0;
    }

    /**
     * 生成四位随机码.
     *
     * @return 四位随机码
     */
    private String randomFourDigitCode() {
        int code = ThreadLocalRandom.current().nextInt(10000);
        return String.format(Locale.ROOT, "%04d", code);
    }

    /**
     * 生成插件配置ID.
     *
     * @param pluginName 插件名称
     * @param pluginType 插件类型
     * @param runMode    运行模式
     * @param tenantId   租户ID
     * @return 插件配置ID
     */
    private UUID generatePluginConfigId(String pluginName, String pluginType, String runMode, UUID tenantId) {
        String source = pluginName + pluginType + runMode + tenantId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 填充创建审计字段.
     *
     * @param entity 插件配置实体
     */
    private void fillCreateAudit(PluginConfigEntity entity) {
        Date now = new Date();
        String currentUserName = HttpUtils.getCurrentUserName();
        entity.setCreator(currentUserName);
        entity.setUpdater(currentUserName);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
    }

    /**
     * 填充修改审计字段.
     *
     * @param entity 插件配置实体
     */
    private void fillUpdateAudit(PluginConfigEntity entity) {
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
    }

    /**
     * Entity转Dto.
     *
     * @param entity 插件配置实体
     * @return 插件配置Dto
     */
    private PluginConfigDto toDto(PluginConfigEntity entity) {
        PluginConfigDto dto = new PluginConfigDto();
        dto.setId(entity.getId());
        dto.setPluginName(entity.getPluginName());
        dto.setPluginType(entity.getPluginType());
        dto.setRunMode(entity.getRunMode());
        dto.setDescription(entity.getDescription());
        dto.setPluginParam(entity.getPluginParam());
        dto.setIsTemplate(entity.getIsTemplate());
        dto.setIsDel(entity.getIsDel());
        dto.setTenantId(entity.getTenantId());
        dto.setCreator(entity.getCreator());
        dto.setUpdater(entity.getUpdater());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
