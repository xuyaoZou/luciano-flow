package com.luciano.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 * - operationLogExecutor: API 操作日志异步写入
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("operationLogExecutor")
    public Executor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("oplog-");
        executor.setRejectedExecutionHandler((r, e) -> {
            // 队列满时直接丢弃（日志写入不能影响主流程）
        });
        executor.initialize();
        return executor;
    }
}