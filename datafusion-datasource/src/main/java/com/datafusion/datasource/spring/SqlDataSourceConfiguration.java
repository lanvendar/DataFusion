package com.datafusion.datasource.spring;

import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.datasource.ConnectorFactory;
import com.datafusion.datasource.annotation.SqlAspect;
import com.datafusion.datasource.model.DataSourceInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 框架核心组件的Spring自动配置类.
 * <p>
 * 负责组装和配置 JFinalSqlBuilder, ConnectorFactory, 和 SqlAspect.
 * 设计为可插拔的,允许用户通过属性文件或自定义Bean来覆盖默认配置.
 * </p>
 *
 * @author lanvendar
 * @version 3.0.0, 2023/9/14
 * @since 2023/9/14
 */
@Component
public class SqlDataSourceConfiguration {
    
    /**
     * 配置JFinal SQL模板引擎的默认Bean.
     * <p>
     * 用户可以定义自己的 JFinalSqlBuilder bean 来覆盖此默认实现
     * </p>
     *
     * @return JFinalSqlBuilder 实例
     */
    @Bean("jfinalSqlBuilder") // 明确bean的名称
    public JFinalSqlBuilder jfinalSqlBuilder() {
        // 提供一个合理的默认配置
        return JFinalSqlBuilder.create()
                .devMode(false) // 生产环境应默认为false
                .sqlTplPath("sql")
                .build();
    }
    
    /**
     * 配置数据源连接器工厂的默认Bean.
     * <p>
     * 用户可以定义自己的 ConnectorFactory bean 来覆盖此默认实现
     * </p>
     *
     * @param defaultDataSource  可选的默认数据源，通过@Autowired注入
     * @param dynamicDataSources 可选的动态数据源列表，通过@Autowired注入
     * @return ConnectorFactory 实例
     */
    @Bean("connectorFactory")
    public ConnectorFactory connectorFactory(
            Optional<DataSourceInfo> defaultDataSource,
            @Autowired(required = false) List<DataSourceInfo> dynamicDataSources) {
        
        return new ConnectorFactory(
                defaultDataSource.orElse(null),
                dynamicDataSources
        );
    }
    
    /**
     * 配置核心的SQL执行切面的默认Bean.
     * <p>
     * 用户可以定义自己的 SqlAspect bean 来覆盖此默认实现
     * </p>
     *
     * @param jfinalSqlBuilder SQL渲染引擎
     * @param connectorFactory 数据源连接工厂
     * @return SqlAspect 实例
     */
    @Bean("sqlAspect")
    public SqlAspect sqlAspect(JFinalSqlBuilder jfinalSqlBuilder, ConnectorFactory connectorFactory) {
        return new SqlAspect(connectorFactory, jfinalSqlBuilder);
    }
}
