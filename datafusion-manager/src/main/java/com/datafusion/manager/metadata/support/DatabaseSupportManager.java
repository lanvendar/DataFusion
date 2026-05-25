package com.datafusion.manager.metadata.support;

import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源服务管理.
 *
 * @author david
 * @version 3.6.4, 2024/8/21
 * @since 3.6.4, 2024/8/21
 */
@Slf4j
@Service
public class DatabaseSupportManager {

    /**
     * 数据源服务缓存.
     */
    private static final Map<DatabaseTypeEnum, MetaDataSupport> META_DATA_CACHE = new HashMap<>();

    /**
     * 数据源服务缓存.
     */
    private static final Map<DatabaseTypeEnum, TransformSupport> TRANSFORM_CACHE = new HashMap<>();

    /**
     * 构造函数.
     *
     * @param list 数据源服务集合
     * @param list2 数据源服务集合
     */
    public DatabaseSupportManager(List<MetaDataSupport> list, List<TransformSupport> list2) {
        list.forEach(svc -> META_DATA_CACHE.put(svc.support(), svc));
        list2.forEach(svc -> TRANSFORM_CACHE.put(svc.support(), svc));
    }

    /**
     * 获取数据源服务.
     *
     * @param schema 实体
     * @return 数据源服务
     */
    public static MetaDataSupport getMetaDataSupport(DataSourceInfoEntity schema) {
        return getMetaDataSupport(schema.getDatabaseType());
    }

    /**
     * 获取数据源服务.
     *
     * @param dbType 数据库类型
     * @return 数据源服务
     */
    public static MetaDataSupport getMetaDataSupport(DatabaseTypeEnum dbType) {
        return META_DATA_CACHE.get(dbType);
    }

    /**
     * 获取数据源服务.
     *
     * @param dbType 数据库类型
     * @return 数据源服务
     */
    public static MetaDataSupport getMetaDataSupport(String dbType) {
        return getMetaDataSupport(DatabaseTypeEnum.fromString(dbType));
    }

    /**
     * 获取数据源服务.
     *
     * @param schema 实体
     * @return 数据源服务
     */
    public static TransformSupport getTransformSupport(DataSourceInfoEntity schema) {
        return getTransformSupport(schema.getDatabaseType());
    }

    /**
     * 获取数据源服务.
     *
     * @param dbType 数据库类型
     * @return 数据源服务
     */
    public static TransformSupport getTransformSupport(String dbType) {
        return getTransformSupport(DatabaseTypeEnum.fromString(dbType));
    }

    /**
     * 获取数据源服务.
     *
     * @param dbType 数据库类型
     * @return 数据源服务
     */
    public static TransformSupport getTransformSupport(DatabaseTypeEnum dbType) {
        return TRANSFORM_CACHE.get(dbType);
    }
}
