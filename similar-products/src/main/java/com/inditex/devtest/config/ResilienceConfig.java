package com.inditex.devtest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

@Configuration
public class ResilienceConfig {

    @Bean
    CircuitBreaker similarIdsCircuitBreaker(ResilienceProperties properties) {
        return CircuitBreaker.of("similar-ids",
                ResiliencePolicy.circuitBreakerConfig(properties.similarIds()));
    }

    @Bean
    CircuitBreaker productDetailCircuitBreaker(ResilienceProperties properties) {
        return CircuitBreaker.of("product-detail",
                ResiliencePolicy.circuitBreakerConfig(properties.productDetail()));
    }

    @Bean
    Retry similarIdsRetry(ResilienceProperties properties) {
        return Retry.of("similar-ids", ResiliencePolicy.retryConfig(properties.retry()));
    }

    @Bean
    Retry productDetailRetry(ResilienceProperties properties) {
        return Retry.of("product-detail", ResiliencePolicy.retryConfig(properties.retry()));
    }
}