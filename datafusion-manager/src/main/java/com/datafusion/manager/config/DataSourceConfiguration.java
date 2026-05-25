package com.datafusion.manager.config;

import com.datafusion.datasource.model.DataSourceInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Properties;

/**
 * 动态数据源自动配置类.
 * 负责从配置属性中解析并创建 DataSourceInfo 相关的Bean.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/8
 * @since 2025/8/8
 */
@Configuration
@DependsOn("environment")
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceConfiguration {
    /**
     * spring环境信息.
     */
    @Autowired
    Environment environment;
    
    /**
     * 数据源信息.
     */
    @Autowired
    DataSourceProperties dataSourceProperties;
    
    /**
     * 创建自定义数据源工厂.
     *
     * @return 自定义数据源工厂
     */
    @Bean("dynamicDataSources")
    public List<DataSourceInfo> dynamicDataSourceInfoList() {
        return dataSourceProperties.getDataSourceInfos();
    }
    
    /**
     * 读取spring配置文件中的数据源.
     *
     * @return 数据库连接信息 DataSourceInfo 对象.
     */
    @Bean("defaultDataSource")
    @ConditionalOnBean
    public DataSourceInfo defaultDataSource() {
        //从运行环境中获取数据源
        Iterable<ConfigurationPropertySource> sources = ConfigurationPropertySources.get(environment);
        Binder binder = new Binder(sources);
        BindResult<Properties> bindResult = binder.bind("spring.datasource", Properties.class);
        if (bindResult.isBound()) {
            Properties properties = bindResult.get();
            if (properties != null) {
                DataSourceInfo dataSourceInfo = new DataSourceInfo();
                dataSourceInfo.setDriverClass(properties.getProperty("driverClassName"));
                dataSourceInfo.setJdbcUrl(properties.getProperty("url"));
                dataSourceInfo.setUsername(properties.getProperty("username"));
                dataSourceInfo.setPassword(properties.getProperty("password"));
                return dataSourceInfo;
            }
        }
        return null;
    }
}
