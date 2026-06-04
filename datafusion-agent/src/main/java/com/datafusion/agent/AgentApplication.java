package com.datafusion.agent;

import com.datafusion.agent.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * DataFusion Agent 应用入口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.datafusion")
@EnableConfigurationProperties(AgentProperties.class)
public class AgentApplication {

    /**
     * 应用入口.
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
