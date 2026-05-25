package com.datafusion.manager.config;

import com.datafusion.datasource.model.DataSourceInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通过 "datafusion.datasource" 前缀从配置文件中绑定所有数据源配置.
 * 提供了更清晰、可管理的配置结构.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/8
 * @since 2025/8/8
 */
@ConfigurationProperties(prefix = "datafusion.datasource")
@Data
@Slf4j
public class DataSourceProperties {
    /**
     * 读取配置文件数据源信息集合.
     */
    private List<DataSourceInfo> dataSourceInfos;
    
    /**
     * 数据源信息Map.
     */
    private volatile Map<UUID, DataSourceInfo> dataSourceMap;
    
    /**
     * 初始化Map.
     */
    @PostConstruct
    public void init() {
        if (CollectionUtils.isNotEmpty(dataSourceInfos)) {
            if (dataSourceMap == null) {
                log.info("初始化map");
                dataSourceMap = dataSourceInfos.stream()
                        .collect(Collectors.toMap(DataSourceInfo::getId, Function.identity()));
            }
        }
    }
}
