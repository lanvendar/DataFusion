package com.datafusion.manager.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.system.dto.PluginConfigDto;
import com.datafusion.manager.system.dto.PluginConfigQueryDto;
import com.datafusion.manager.system.dto.PluginConfigSaveDto;
import com.datafusion.manager.system.dto.PluginConfigUpdateDto;
import com.datafusion.manager.system.po.PluginConfigEntity;

import java.util.List;
import java.util.UUID;

/**
 * 系统-插件配置Service.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
public interface PluginConfigService extends IService<PluginConfigEntity> {

    /**
     * 分页查询插件配置.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<PluginConfigDto> pagePluginConfig(PageQuery<PluginConfigQueryDto> query);

    /**
     * 查询插件配置列表.
     *
     * @param query 查询条件
     * @return 插件配置列表
     */
    List<PluginConfigDto> listPluginConfig(PluginConfigQueryDto query);

    /**
     * 根据ID查询插件配置.
     *
     * @param id 插件配置ID
     * @return 插件配置详情
     */
    PluginConfigDto getPluginConfigById(UUID id);

    /**
     * 新增插件配置.
     *
     * @param dto 新增参数
     * @return 插件配置ID
     */
    UUID addPluginConfig(PluginConfigSaveDto dto);

    /**
     * 复制插件配置.
     *
     * @param dto 复制参数
     * @return 新插件配置ID
     */
    UUID copyPluginConfig(PluginConfigSaveDto dto);

    /**
     * 修改插件配置.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updatePluginConfig(PluginConfigUpdateDto dto);

    /**
     * 删除插件配置.
     *
     * @param id 插件配置ID
     * @return 是否成功
     */
    boolean deletePluginConfig(UUID id);

    /**
     * 按插件类型和运行模式获取插件配置.
     *
     * @param pluginType 插件类型
     * @param runMode    运行模式
     * @return 插件配置实体
     */
    PluginConfigEntity getPluginConfigByTypeAndMode(String pluginType, String runMode);
}
