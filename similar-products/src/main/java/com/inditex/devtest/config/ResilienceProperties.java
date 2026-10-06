package com.inditex.devtest.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("similar-products.resilience")
public record ResilienceProperties(CircuitBreakerSettings similarIds, CircuitBreakerSettings productDetail,
        RetrySettings retry) {

    public record CircuitBreakerSettings(float failureRateThreshold, int minimumNumberOfCalls,
            int slidingWindowSize, Duration waitDurationInOpenState) {
    }

    public record RetrySettings(int maxAttempts, Duration waitDuration) {
    }
}