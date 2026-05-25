package com.datafusion.manager;

import com.datafusion.datasource.spring.SqlScan;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 项目启动类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/24
 * @since 2025/7/24
 */
@SpringBootApplication(scanBasePackages = {"com.datafusion"})
@SqlScan(basePackages = {"com.datafusion.manager.*"})
@ComponentScan(
        basePackages = {"com.datafusion"},
        excludeFilters = @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.datafusion\\.manager\\.asset2\\..*"
    )
)
@MapperScan(basePackages = {
        "com.datafusion.manager.asset.dao",
        "com.datafusion.manager.ingestion.dao",
        "com.datafusion.manager.metadata.dao",
        "com.datafusion.manager.scheduler.dao",
        "com.datafusion.manager.development.dao"
})
@EnableScheduling
@Slf4j
public class ManagerApplication {
    /**
     * 主函数.
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        System.setProperty("calcite.default.charset", "UTF-8");
        System.setProperty("calcite.default.nationalcharset", "UTF-8");
        SpringApplication.run(ManagerApplication.class, args);
    }
}
