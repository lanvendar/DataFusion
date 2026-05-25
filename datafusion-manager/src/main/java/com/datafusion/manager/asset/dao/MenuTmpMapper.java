package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.asset.dto.response.AppResp;
import com.datafusion.manager.asset.po.MenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 菜单Mapper接口.
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/13
 * @since 2025/11/13
 */
@Mapper
public interface MenuTmpMapper extends BaseMapper<MenuEntity> {

    /**
     * 同步菜单数据到临时表.
     * 清空临时表后，从源表重新插入数据.
     */
    void syncMenuFromDb();

    /**
     * 查询应用名称列表.
     *
     * @return 应用名称列表
     */
    List<AppResp> getAppNameList();

    /**
     * 查询菜单树（不包含叶子节点过滤）.
     *
     * @param appCode 应用code
     * @return 菜单列表
     */
    List<MenuEntity> getMenuTreeByAppCode(@Param("appCode") String appCode);

    /**
     * 清空临时表.
     */
    void truncateTable();

    /**
     * 根据component_url批量查询菜单.
     *
     * @param componentUrls component_url列表
     * @return 菜单列表
     */
    List<MenuEntity> selectMenusByComponentUrls(@Param("componentUrls") List<String> componentUrls);

    /**
     * 根据app_code和component_url批量查询菜单.
     *
     * @param appCode       应用code
     * @param componentUrls component_url列表
     * @return 菜单列表
     */
    List<MenuEntity> selectMenusByAppCodeAndUrls(@Param("appCode") String appCode, @Param("componentUrls") List<String> componentUrls);

    /**
     * 插入未匹配的菜单记录.
     *
     * @param id             主键ID
     * @param projectCode   项目编码
     * @param weLocation    前端页面路径
     * @param apiResourceId 关联的API资源ID
     * @param creator       创建人
     * @param createTime    创建时间
     * @param updater       更新人
     * @param updateTime    更新时间
     */
    void insertUnmatchedMenu(@Param("id") UUID id, @Param("projectCode") String projectCode,
            @Param("weLocation") String weLocation, @Param("apiResourceId") UUID apiResourceId,
            @Param("creator") String creator, @Param("createTime") Date createTime,
            @Param("updater") String updater, @Param("updateTime") Date updateTime);

}
