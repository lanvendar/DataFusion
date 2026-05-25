package com.datafusion.manager.asset.dto.builder;

import lombok.Data;

import java.util.List;

/**
 * 节点属性构建器.
 *
 * @author zhengjiexiang
 * @since 2026/1/23
 * @version 1.0.0
 */
public class NodePropBuilder {

    /**
     * 节点属性类型枚举.
     */
    public enum NodePropType {
        /**
         * API节点属性.
         */
        API(ApiNodeProp.class),
        /**
         * 指标节点属性.
         */
        METRIC(MetricNodeProp.class),
        /**
         * 表节点属性.
         */
        TABLE(TableNodeProp.class),
        /**
         * 列节点属性.
         */
        COLUMN(ColumnNodeProp.class),
        /**
         * 菜单节点属性.
         */
        MENU(MenuNodeProp.class);

        /**
         * 节点属性类.
         */
        private final Class<?> clazz;

        /**
         * 构造函数.
         *
         * @param clazz 节点属性类
         */
        NodePropType(Class<?> clazz) {
            this.clazz = clazz;
        }

        public Class<?> getClazz() {
            return clazz;
        }
    }

    /**
     * 根据类型创建节点属性实例.
     *
     * @param type 节点属性类型
     * @param <T>  泛型
     * @return 创建的实例
     * @throws RuntimeException 创建实例失败
     */
    public static <T> T builder(NodePropType type) {
        try {
            Class<T> clazz = (Class<T>) type.getClazz();
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建节点属性实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * API节点属性.
     */
    @Data
    public static class ApiNodeProp {
        /**
         * urn.
         */
        private String urn;

        /**
         * 服务编码.
         */
        private String serviceCode;

        /**
         * 服务名称.
         */
        private String serviceName;

        /**
         * url.
         */
        private String url;

        /**
         * url名称.
         */
        private String urlName;

        /**
         * weLocation.
         */
        List<ResourceSnapshotBuilder.WeLocation> weLocationList;

    }

    /**
     * 指标节点属性.
     */
    @Data
    public static class MetricNodeProp {
        /**
         * urn.
         */
        private String urn;

        /**
         * 服务编码.
         */
        private String serviceCode;

        /**
         * 服务名称.
         */
        private String serviceName;

        /**
         * url.
         */
        private String url;

        /**
         * url名称.
         */
        private String urlName;

        /**
         * 指标编码.
         */
        private String metricCode;

        /**
         * 指标名称.
         */
        private String metricName;

        /**
         * 维度.
         */
        private String dimension;

        /**
         * 物理层级.
         */
        private String physicalLevel;

        /**
         * 计算时效.
         */
        private String timeliness;

        /**
         * 类型.
         */
        private String type;
    }

    /**
     * 表节点属性.
     */
    @Data
    public static class TableNodeProp {
        /**
         * urn.
         */
        private String urn;

        /**
         * 数据库类型.
         */
        private String databaseType;

        /**
         * 数据源名称.
         */
        private String datasourceName;

        /**
         * 数据库名称.
         */
        private String databaseName;

        /**
         * Schema名称.
         */
        private String schemaName;

        /**
         * 表名称.
         */
        private String tableName;

        /**
         * 表描述.
         */
        private String tableDesc;
    }

    /**
     * 字段节点属性.
     */
    @Data
    public static class ColumnNodeProp {
        /**
         * urn.
         */
        private String urn;

        /**
         * 数据库类型.
         */
        private String databaseType;

        /**
         * 数据源名称.
         */
        private String datasourceName;

        /**
         * 数据库名称.
         */
        private String databaseName;

        /**
         * Schema名称.
         */
        private String schemaName;

        /**
         * 表名称.
         */
        private String tableName;

        /**
         * 表描述.
         */
        private String tableDesc;

        /**
         * 列名称.
         */
        private String columnName;

        /**
         * 列描述.
         */
        private String columnDesc;
    }

    /**
     * Menu节点属性.
     */
    @Data
    public static class MenuNodeProp {
        /**
         * urn.
         */
        private String urn;

        /**
         * 应用code.
         */
        private String appCode;

        /**
         * 应用名称.
         */
        private String appName;

        /**
         * 菜单路径 菜单1-菜单2-菜单3.
         */
        private String menu;

        /**
         * 菜单类型.
         */
        private Byte componentType;
    }
}
