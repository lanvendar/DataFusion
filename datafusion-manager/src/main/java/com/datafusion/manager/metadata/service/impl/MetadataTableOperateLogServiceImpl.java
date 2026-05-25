package com.datafusion.manager.metadata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.metadata.dao.MetadataTableOperateLogMapper;
import com.datafusion.manager.metadata.po.MetadataTableOperateLogEntity;
import com.datafusion.manager.metadata.service.MetadataTableOperateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 表结构同步记录表服务.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/15
 * @since 2025/9/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataTableOperateLogServiceImpl extends ServiceImpl<MetadataTableOperateLogMapper, MetadataTableOperateLogEntity>
        implements MetadataTableOperateLogService {
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addLog(MetadataTableOperateLogEntity saveDto) {
    
        MetadataTableOperateLogEntity entity = getById(saveDto.getId());
        if (null == entity) {
            boolean saved = save(saveDto);
            if (!saved) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据库保存失败");
            }
        } else {
            boolean saved = updateLog(saveDto);
            if (!saved) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据库更新失败");
            }
        }
       
        return saveDto.getId();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateLog(MetadataTableOperateLogEntity updateDto) {
        return updateById(updateDto);
    }
    
    @Override
    public MetadataTableOperateLogEntity getWithCheckNonNull(UUID id) {
        MetadataTableOperateLogEntity entity = super.getById(id);
        if (null == entity) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "未知错误，请刷新页面重试");
        }
        return entity;
    }
    
    @Override
    public IPage<MetadataTableOperateLogEntity> selectPage(IPage<MetadataTableOperateLogEntity> page, Wrapper wrapper) {
        return baseMapper.selectPage(page, wrapper);
    }
}
