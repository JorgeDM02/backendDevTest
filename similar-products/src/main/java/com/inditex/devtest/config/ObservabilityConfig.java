package com.inditex.devtest.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

@Configuration
public class ObservabilityConfig {

    static final String DEGRADATIONS = "similar.products.downstream.degradations";

    @Bean
    MeterBinder circuitBreakerMetrics(
            @Qualifier("similarIdsCircuitBreaker") CircuitBreaker similarIdsCircuitBreaker,
            @Qualifier("productDetailCircuitBreaker") CircuitBreaker productDetailCircuitBreaker) {
        return meterRegistry -> {
            registerCircuitBreaker(meterRegistry, similarIdsCircuitBreaker);
            registerCircuitBreaker(meterRegistry, productDetailCircuitBreaker);
        };
    }

    @Bean
    MeterBinder degradationMetrics() {
        return meterRegistry -> {
            for (String client : List.of("similar-ids", "product-detail")) {
                Counter.builder(DEGRADATIONS)
                        .description("Downstream lookups abandoned because the API failed or its circuit was open")
                        .tag("client", client)
                        .register(meterRegistry);
            }
        };
    }

    private static void registerCircuitBreaker(MeterRegistry meterRegistry, CircuitBreaker circuitBreaker) {
        Gauge.builder("similar.products.circuit.breaker.state", circuitBreaker,
                        breaker -> breaker.getState().getOrder())
                .tag("name", circuitBreaker.getName())
                .description("0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=DISABLED, 4=FORCED_OPEN, 5=METRICS_ONLY")
                .register(meterRegistry);
        Gauge.builder("similar.products.circuit.breaker.failed.calls", circuitBreaker,
                        breaker -> breaker.getMetrics().getNumberOfFailedCalls())
                .tag("name", circuitBreaker.getName())
                .register(meterRegistry);
        Gauge.builder("similar.products.circuit.breaker.not.permitted.calls", circuitBreaker,
                        breaker -> breaker.getMetrics().getNumberOfNotPermittedCalls())
                .tag("name", circuitBreaker.getName())
                .register(meterRegistry);
    }
}