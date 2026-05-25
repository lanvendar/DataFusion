package com.datafusion.datasource.annotation;

import cn.hutool.core.util.StrUtil;
import com.datafusion.datasource.ConnectorFactory;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.datasource.model.ExecuteParam;

import java.util.UUID;

/**
 * SqlDs解析器 {@link SqlDs}.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/25
 * @since 2025/7/25
 */
public class SqlDsResolver {
    /**
     * 解析参数上注解 @SqlDs 的数据源,优先级(参数 -> 方法 -> 类 -> 默认).
     *
     * @param sqlDs   注解
     * @param arg     参数
     * @param factory 数据源工厂
     * @return 数据源ID
     */
    public String resolve(SqlDs sqlDs, Object arg, ConnectorFactory factory) {
        if (arg instanceof DataSourceInfo) {
            DataSourceInfo dataSourceInfo = (DataSourceInfo) arg;
            
            // 1.传入的 DataSourceInfo 带有明确的 ID
            if (dataSourceInfo.getId() != null) {
                factory.addDataSource(dataSourceInfo);
                return dataSourceInfo.getId().toString();
            }
            
            // 2.传入的 DataSourceInfo 没有 ID，视为临时数据源
            UUID tmpId = UUID.nameUUIDFromBytes(ConnectorFactory.TEMP_TEST_DATA_SOURCE.getBytes());
            dataSourceInfo.setId(tmpId);
            // 先关闭可能存在的上一个临时数据源，再添加新的
            // factory.closeDataSources(tmpId.toString());
            factory.addTempDataSource(dataSourceInfo);
            return tmpId.toString();
        } else if (StrUtil.isNotEmpty(sqlDs.id())) {
            // 3.参数不是 DataSourceInfo,但注解本身提供了静态的 dsId
            return sqlDs.id();
        }
        
        // 如果参数不是 DataSourceInfo 且注解没有提供ID，则此参数不贡献数据源信息
        return null;
    }
    
    /**
     * 解析方法/类上注解 @SqlDs 的数据源,优先级(参数 -> 方法 -> 类 -> 默认).
     *
     * @param executeParam 执行参数
     * @param context      方法解析上下文
     * @param dsId         数据源ID
     **/
    public void adaptFinalDsId(ExecuteParam executeParam, MethodResolverContext context, String dsId) {
        // 优先级 1: 来自方法参数的动态数据源ID
        if (StrUtil.isNotEmpty(dsId)) {
            executeParam.setDsId(dsId);
            return;
        }
        
        // 优先级 2: 来自方法上的 @SqlDs 注解
        SqlDs methodDs = context.getMethod().getAnnotation(SqlDs.class);
        if (methodDs != null && StrUtil.isNotEmpty(methodDs.id())) {
            executeParam.setDsId(methodDs.id());
            return;
        }
        
        // 优先级 3: 来自类上的 @SqlDs 注解
        SqlDs classDs = context.getTargetClass().getAnnotation(SqlDs.class);
        if (classDs != null && StrUtil.isNotEmpty(classDs.id())) {
            executeParam.setDsId(classDs.id());
            return;
        }
        
        // 优先级 4: 回退到默认数据源
        String defaultDsId = UUID.nameUUIDFromBytes(ConnectorFactory.DEFAULT_DATA_SOURCE.getBytes()).toString();
        executeParam.setDsId(defaultDsId);
    }
}
