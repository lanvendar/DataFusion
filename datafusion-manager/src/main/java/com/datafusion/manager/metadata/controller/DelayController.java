package com.datafusion.manager.metadata.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 优雅停服.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/2
 * @since 2025/9/2
 */
@RestController
public class DelayController {
    /**
     * 模拟延迟响应.
     *
     * @param delaySeconds 延迟秒数
     * @return 延迟信息
     * @throws InterruptedException 线程中断异常
     */
    @GetMapping("/delayed-response")
    public String delayedResponse(@RequestParam(defaultValue = "10") long delaySeconds) throws InterruptedException {
        // 记录请求开始的时间
        long startTime = System.currentTimeMillis();
        
        // 模拟延迟（请求延时，确保返回结果至少为 delaySeconds 秒）
        TimeUnit.SECONDS.sleep(delaySeconds);
        
        // 记录请求结束的时间
        long endTime = System.currentTimeMillis();
        
        // 计算请求时长
        long requestDuration = endTime - startTime;
        
        // 返回时长信息
        return "Request took " + requestDuration + " milliseconds. Delayed for " + delaySeconds + " seconds.";
    }
}

