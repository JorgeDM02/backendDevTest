package com.inditex.devtest.config;

import org.springframework.web.client.HttpServerErrorException;

import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;

public final class ResiliencePolicy {

    private ResiliencePolicy() {
    }

    public static CircuitBreakerConfig circuitBreakerConfig(
            ResilienceProperties.CircuitBreakerSettings settings) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .slidingWindowSize(settings.slidingWindowSize())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .ignoreExceptions(ProductNotFoundException.class)
                .build();
    }

    public static RetryConfig retryConfig(ResilienceProperties.RetrySettings settings) {
        return RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .waitDuration(settings.waitDuration())
                .retryOnException(failure -> failure instanceof HttpServerErrorException)
                .build();
    }
}