package com.datafusion.manager.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.system.dao.VariableInfoMapper;
import com.datafusion.manager.system.dto.VariableInfoDto;
import com.datafusion.manager.system.dto.VariableInfoQueryDto;
import com.datafusion.manager.system.dto.VariableInfoSaveDto;
import com.datafusion.manager.system.dto.VariableInfoUpdateDto;
import com.datafusion.manager.system.po.VariableInfoEntity;
import com.datafusion.manager.system.service.VariableInfoService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统-变量信息Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class VariableInfoServiceImpl extends ServiceImpl<VariableInfoMapper, VariableInfoEntity>
        implements VariableInfoService {

    /**
     * 变量类型: 系统全局.
     */
    private static final String TYPE_SYSTEM = "SYSTEM";

    /**
     * 变量类型: 自定义.
     */
    private static final String TYPE_CUSTOM = "CUSTOM";

    @Override
    public PageResponse<VariableInfoDto> pageVariableInfo(PageQuery<VariableInfoQueryDto> query) {
        LambdaQueryWrapper<VariableInfoEntity> wrapper = buildQueryWrapper(query.getOption());
        IPage<VariableInfoEntity> page = baseMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);

        PageResponse<VariableInfoDto> response = new PageResponse<>();
        response.setDataList(page.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<VariableInfoDto> listVariableInfo(VariableInfoQueryDto query) {
        LambdaQueryWrapper<VariableInfoEntity> wrapper = buildQueryWrapper(query);
        return baseMapper.selectList(wrapper).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public VariableInfoDto getVariableInfoById(UUID id) {
        VariableInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "变量不存在");
        }
        return toDto(entity);
    }

    @Override
    public UUID addVariableInfo(VariableInfoSaveDto dto) {
        checkCodeUnique(dto.getCode(), null);

        VariableInfoEntity entity = new VariableInfoEntity();
        entity.setId(UUID.nameUUIDFromBytes(dto.getCode().getBytes()));
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setType(TYPE_CUSTOM);
        entity.setValueType(dto.getValueType());
        entity.setValue(dto.getValue());
        entity.setRemark(dto.getRemark());

        Date now = new Date();
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateVariableInfo(VariableInfoUpdateDto dto) {
        VariableInfoEntity entity = getById(dto.getId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "变量不存在");
        }

        if (TYPE_SYSTEM.equals(entity.getType())) {
            // SYSTEM变量仅允许修改value
            if (dto.getValue() != null) {
                entity.setValue(dto.getValue());
            }
        } else {
            // CUSTOM变量: 合并非空字段
            if (StringUtils.isNotBlank(dto.getCode())) {
                checkCodeUnique(dto.getCode(), dto.getId());
                entity.setCode(dto.getCode());
            }
            if (StringUtils.isNotBlank(dto.getName())) {
                entity.setName(dto.getName());
            }
            if (StringUtils.isNotBlank(dto.getValueType())) {
                entity.setValueType(dto.getValueType());
            }
            if (dto.getValue() != null) {
                entity.setValue(dto.getValue());
            }
            if (dto.getRemark() != null) {
                entity.setRemark(dto.getRemark());
            }
        }

        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return updateById(entity);
    }

    @Override
    public boolean deleteVariableInfo(UUID id) {
        VariableInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "变量不存在");
        }
        if (TYPE_SYSTEM.equals(entity.getType())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "系统变量不允许删除");
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
    private LambdaQueryWrapper<VariableInfoEntity> buildQueryWrapper(VariableInfoQueryDto query) {
        LambdaQueryWrapper<VariableInfoEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getName())) {
                wrapper.like(VariableInfoEntity::getName, query.getName());
            }
            if (StringUtils.isNotBlank(query.getCode())) {
                wrapper.like(VariableInfoEntity::getCode, query.getCode());
            }
            if (StringUtils.isNotBlank(query.getType())) {
                wrapper.eq(VariableInfoEntity::getType, query.getType());
            }
            if (StringUtils.isNotBlank(query.getValueType())) {
                wrapper.eq(VariableInfoEntity::getValueType, query.getValueType());
            }
        }
        wrapper.orderByDesc(VariableInfoEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 校验code唯一性.
     *
     * @param code      变量编码
     * @param excludeId 排除的ID(修改时排除自身)
     */
    private void checkCodeUnique(String code, UUID excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        LambdaQueryWrapper<VariableInfoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VariableInfoEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(VariableInfoEntity::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "变量编码已存在");
        }
    }

    /**
     * Entity转Dto.
     *
     * @param entity 变量实体
     * @return 变量Dto
     */
    private VariableInfoDto toDto(VariableInfoEntity entity) {
        VariableInfoDto dto = new VariableInfoDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setValueType(entity.getValueType());
        dto.setValue(entity.getValue());
        dto.setRemark(entity.getRemark());
        dto.setCreator(entity.getCreator());
        dto.setUpdater(entity.getUpdater());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
    // endregion
}
