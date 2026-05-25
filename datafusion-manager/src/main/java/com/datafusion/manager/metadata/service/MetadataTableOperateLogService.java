package com.datafusion.manager.metadata.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.datafusion.manager.metadata.po.MetadataTableOperateLogEntity;

import java.util.UUID;

/**
 * 表结构同步记录表服务.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/15
 * @since 2025/9/15
 */
public interface MetadataTableOperateLogService {
    
    /**
     * 添加日志.
     * @param saveDto 实体
     * @return 日志注解uuid
     */
    UUID addLog(MetadataTableOperateLogEntity saveDto);
    
    /**
     * 更新日志.
     * @param updateDto 实体
     * @return 更新是否成功
     */
    boolean updateLog(MetadataTableOperateLogEntity updateDto);
    
    /**
     * 查询日志信息.
     * @param id 主键id
     * @return 日志信息
     */
    MetadataTableOperateLogEntity getWithCheckNonNull(UUID id);
    
    /**
     * 分页查询.
     * @param page 分页信息
     * @param wrapper 查询条件
     * @return 分页查询结果
     */
    IPage<MetadataTableOperateLogEntity> selectPage(IPage<MetadataTableOperateLogEntity> page, Wrapper wrapper);
}
