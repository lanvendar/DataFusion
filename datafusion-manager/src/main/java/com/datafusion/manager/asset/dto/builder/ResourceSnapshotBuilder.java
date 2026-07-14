package com.datafusion.manager.asset.dto.builder;

import cn.hutool.core.bean.BeanUtil;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.dto.MetricColumnInfo;
import com.datafusion.manager.asset.dto.skywalking.CallEdge;
import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 资源快照构建器.
 * @author zhengjiexiang
 * @since 2026/1/23
 * @version 1.0.0
 */
public class ResourceSnapshotBuilder {

    /**
     * 根据枚举类型创建对应实例.
     * @param resourceSnapshotType 资源快照类型
     * @param <T> 泛型
     * @return 创建的实例
     * @throws RuntimeException 创建实例失败
     */
    public static <T> T builder(ResourceSnapshotType resourceSnapshotType) {
        try {
            Class<T> clazz = (Class<T>) resourceSnapshotType.getClazz();
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据资源创建对应快照实例.
     * @param entity 资源
     * @param snapshotExtractor 对象
     * @param <T> 泛型
     * @return 创建的实例
     * @throws RuntimeException 创建实例失败
     */
    public static <T> T builder(AssetLineageResourceEntity entity,
            Function<AssetLineageResourceEntity, Object> snapshotExtractor) {
        if (entity == null) {
            return null;
        }
        try {
            // 1. 获取目标类型
            ResourceSnapshotType resourceSnapshotType = ResourceSnapshotType.valueOf(entity.getResourceType());
            Class<T> clazz = (Class<T>) resourceSnapshotType.getClazz();

            // 2. 使用传入的提取器获取数据
            Object snapshotData = snapshotExtractor.apply(entity);

            // 3. 如果数据为null，返回空实例
            if (snapshotData == null) {
                return clazz.getDeclaredConstructor().newInstance();
            }

            // 4. 转换并返回
            return JacksonUtils.tryObj2Bean(snapshotData, clazz);

        } catch (Exception e) {
            throw new RuntimeException("创建快照实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 默认方法（读原有的 getResourceSnapshot）.
     * @param entity 资源
     * @param <T> 泛型
     * @return 创建的实例
     */
    public static <T> T builder(AssetLineageResourceEntity entity) {
        return builder(entity, AssetLineageResourceEntity::getResourceSnapshot);
    }

    /**
     * 拓展方法（读原有的 getResourceSnapshot）.
     * @param entity 资源
     * @param isResult 是否是结果快照
     * @param <T> 泛型
     * @return 创建的实例
     */
    public static <T> T builder(AssetLineageResourceEntity entity, boolean isResult) {
        if (entity == null) {
            return null;
        }
        try {
            String resourceType = entity.getResourceType();
            ResourceSnapshotType snapshotType;
            Class<T> clazz;

            if (isResult) {
                // 读取 resultSnapshot 时使用 Result 类型
                snapshotType = ResourceSnapshotType.valueOfResultType(resourceType);
                clazz = (Class<T>) snapshotType.getResultClazz();
            } else {
                // 读取 resourceSnapshot 时使用原始类型
                snapshotType = ResourceSnapshotType.valueOf(resourceType);
                clazz = (Class<T>) snapshotType.getClazz();
            }

            Object snapshotData = isResult
                    ? entity.getResultSnapshot()
                    : entity.getResourceSnapshot();

            if (snapshotData == null) {
                return clazz.getDeclaredConstructor().newInstance();
            }

            return JacksonUtils.tryObj2Bean(snapshotData, clazz);
        } catch (Exception e) {
            throw new RuntimeException("创建快照实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 基于api创建指标快照.
     * @param entity 资源
     * @return 创建的实例
     * @throws RuntimeException 创建实例失败
     */
    public static MetricResourceSnapshot builder(ApiResourceSnapshot entity) {
        MetricResourceSnapshot metricResourceSnapshot = new MetricResourceSnapshot();
        BeanUtil.copyProperties(entity, metricResourceSnapshot);
        return metricResourceSnapshot;
    }

    public enum ResourceSnapshotType {
        /**
         * API.
         */
        API(ApiResourceSnapshot.class, ApiResourceResultSnapshot.class),
        /**
         * 指标.
         */
        METRIC(MetricResourceSnapshot.class, MetricResourceResultSnapshot.class),
        /**
         * GUI/菜单.
         */
        GUI(MenuResourceSnapshot.class, MenuResourceResultSnapshot.class);

        /**
         * 构造函数.
         */
        private final Class clazz;

        /** 资源结果快照类型. */
        private final Class resultClazz;

        /**
         * 构造函数.
         */
        ResourceSnapshotType(Class<?> resourceSnapshotClass, Class<?> resultSnapshotClass) {
            this.clazz = resourceSnapshotClass;
            this.resultClazz = resultSnapshotClass;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public Class<?> getResultClazz() {
            return resultClazz;
        }

        /**
         * 根据资源类型获取对应的 Result 类型枚举值.
         * @param resourceType 资源类型
         * @return Result 类型枚举值
         */
        public static ResourceSnapshotType valueOfResultType(String resourceType) {
            return valueOf(resourceType);
        }
    }

    /**
     * 调用链血缘数据.
     */
    @Data
    public static class CallChain {

        /**
         * Trace ID.
         */
        private String traceId;

        /**
         * weLocationList.
         */
        private List<WeLocation> weLocationList;

        /**
         * 调用边列表.
         */
        private List<CallEdge> callEdges;
    }

    /**
     * 绑定菜单关系.
     */
    @Data
    @EqualsAndHashCode(of = {"projectName", "weLocation"})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WeLocation {

        /**
         * projectName.
         */
        private String projectName;

        /**
         * location.
         */
        private String weLocation;

        /**
         * 测点集合.
         */
        private Set<MetricsTagDto> tagSet;
    }

    /**
     * 资源快照.
     */
    @Data
    public static class ApiResourceSnapshot {

        /**
         * 组织名称.
         */
        private String organization;

        /**
         * 业务域.
         */
        private String businessDomain;

        /**
         * 环境.
         */
        private String env;

        /**
         * 服务类型.
         */
        private String serviceType;

        /**
         * 服务英文名称.
         */
        private String serviceEnName;

        /**
         * 请求类型.
         */
        private String requestType;

        /**
         * 请求URL.带前缀 如/api/openapi/v1/users/login.
         */
        private String requestUrl;

        /**
         * 前缀 basePath. 如/api/openapi
         */
        private String basePath;

        /**
         * 维度.
         */
        private String dimension;

        /**
         * 服务中文名称.
         */
        private String serviceCnName;

        /**
         * 接口名称.
         */
        private String requestUrlName;

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

        /**
         * 统一指标主键id.
         */
        private String tagInfoId;
    }

    /**
     * 资源解析结果快照.
     */
    @Data
    public static class ApiResourceResultSnapshot {

        /**
         * 调用链血缘数据.
         */
        private CallChain callChain;

    }

    /**
     * 资源快照.
     */
    @Data
    public static class MetricResourceSnapshot {

        /**
         * 外部指标唯一ID. 外部同步场景下有值
         */
        private String thirdMetricId;

        /**
         * 页面选择的父级资源ID.
         */
        private UUID parentResourceId;

        /**
         * 组织名称.
         */
        private String organization;

        /**
         * 业务域.
         */
        private String businessDomain;

        /**
         * 环境.
         */
        private String env;

        /**
         * 服务类型.
         */
        private String serviceType;

        /**
         * 服务英文名称.
         */
        private String serviceEnName;

        /**
         * 请求类型.
         */
        private String requestType;

        /**
         * 请求URL.与下面的区别是，url带了前缀 如/api/openapi/v1/users/login.
         */
        private String requestUrl;

        /**
         * endpoint. 如/v1/users/login
         */
        private String endPoint;

        /**
         * 维度.
         */
        private String dimension;

        /**
         * 指标code.
         */
        private String code;

        /**
         * 指标名称.
         */
        private String name;

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

        /**
         * 统一指标主键id.
         */
        private String tagInfoId;

    }

    /**
     * 指标血缘信息.
     */
    @Data
    public static class MetricResourceResultSnapshot {

        /**
         * 指标信息.
         */
        private MetricDtoInfo metricDto;
    }

    /**
     * 指标DTO信息.
     */
    @Data
    public static class MetricDtoInfo {

        /**
         * 指标编码.
         */
        private String code;

        /**
         * 指标维度.
         */
        private String dimension;

        /**
         * 指标名称.
         */
        private String name;

        /**
         * 表名称.
         */
        private String tableName;

        /**
         * 列名称.
         */
        private String columnName;

        /**
         * 数据源名称.
         */
        private String datasourceName;

        /**
         * 数据源实例DTO.
         */
        private DataSourceInfoDto dataSourceInfo;

        /**
         * 字段信息列表（V2支持多字段）.
         */
        private List<MetricColumnInfo> columnInfoList;

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

        /**
         * 统一指标主键id.
         */
        private String tagInfoId;
    }

    /**
     * 菜单资源快照.
     */
    @Data
    public static class MenuResourceSnapshot {

        /**
         * 组织名称.
         */
        private String organization;

        /**
         * 业务域.
         */
        private String businessDomain;

        /**
         * 服务名称.
         */
        private String serviceEnName;

        /**
         * 应用编码.
         */
        private String appCode;

        /**
         * 应用名称.
         */
        private String appName;

        /**
         * 菜单ID.
         */
        private Long menuId;

        /**
         * 菜单名称.
         */
        private String menuName;

        /**
         * 菜单类型.
         */
        private Byte componentType;

    }

    /**
     * 菜单资源快照.
     */
    @Data
    public static class MenuResourceResultSnapshot {

        /**
         * 关联的API资源ID列表.
         */
        private Map<UUID, Set<MetricsTagDto>> apiResourceIds;
    }

}
