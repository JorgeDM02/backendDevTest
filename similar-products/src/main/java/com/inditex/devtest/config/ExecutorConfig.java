package com.inditex.devtest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    @Bean
    ThreadPoolTaskExecutor productDetailExecutor(FanOutProperties properties) {
        int maxConcurrentCalls = properties.maxConcurrentCalls();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrentCalls);
        executor.setMaxPoolSize(maxConcurrentCalls);
        executor.setThreadNamePrefix("product-detail-");
        return executor;
    }
}