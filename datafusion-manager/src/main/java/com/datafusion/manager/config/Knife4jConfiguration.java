/*
 * Copyright © 2020-2022 Nimbus Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

package com.datafusion.manager.config;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableKnife4j
public class Knife4jConfiguration {
    
    /**
     * 元数据管理API.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi metadataApi() {
        return groupedOpenApi("元数据管理", "com.datafusion.manager.metadata");
    }
    
    /**
     * 数据集成.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi ingestionApi() {
        return groupedOpenApi("数据集成", "com.datafusion.manager.ingestion");
    }
    
    /**
     * 数据开发.
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi developmentApi() {
        return groupedOpenApi("数据开发", "com.datafusion.manager.development");
    }
    
    /**
     * 数据资产.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi assetApi() {
        return groupedOpenApi("数据资产", "com.datafusion.manager.asset",
                "com.infra.common.file");
    }
    
    /**
     * 系统设置.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi systemApi() {
        return groupedOpenApi("系统设置", "com.datafusion.manager.system");
    }
    
    /**
     * 临时测试.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi testApi() {
        return groupedOpenApi("临时测试", "com.datafusion.manager.master");
    }
    
    /**
     * 调度中心.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi flowAndScheduleApi() {
        return groupedOpenApi("调度中心", "com.datafusion.manager.scheduler");
    }

    /**
     * 数据质量.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi qualityApi() {
        return groupedOpenApi("数据质量", "com.datafusion.manager.quality");
    }
    
    /**
     * APIHUB.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi apihubApi() {
        return groupedOpenApi("ApiHub接口", "com.nimbus.apihub");
    }
    
    /**
     * 数据初始化.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi initApi() {
        return groupedOpenApi("数据初始化", "com.datafusion.manager.init");
    }
    
    /**
     * 运维监控.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi monitoringApi() {
        return groupedOpenApi("运维监控", "com.datafusion.manager.monitoring");
    }
    
    /**
     * 文件管理.
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi fileApi() {
        return groupedOpenApi("文件管理", "com.datafusion.manager.file");
    }
    
    /**
     * 共同 Docket.
     *
     * @param groupName    分组名称
     * @param basePackages 包路径.
     * @return Docket
     */
    private GroupedOpenApi groupedOpenApi(final String groupName, final String... basePackages) {
        return GroupedOpenApi.builder()
                // 组名
                .group(groupName)
                // 扫描的包
                .packagesToScan(basePackages)
                .build();
    }
    
}