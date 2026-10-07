package com.inditex.devtest.similarproducts.infrastructure.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import com.inditex.devtest.config.ResiliencePolicy;
import com.inditex.devtest.config.ResilienceProperties;
import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ResilientSimilarIdsClientTest {

    private static final String DEGRADATIONS = "similar.products.downstream.degradations";

    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private Counter degradations;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("test-similar-ids", ResiliencePolicy.circuitBreakerConfig(
                new ResilienceProperties.CircuitBreakerSettings(50f, 4, 4, Duration.ofSeconds(10))));
        retry = Retry.of("test-similar-ids", ResiliencePolicy.retryConfig(
                new ResilienceProperties.RetrySettings(2, Duration.ZERO)));
        degradations = Counter.builder(DEGRADATIONS).register(new SimpleMeterRegistry());
    }

    @Test
    void returnsSimilarIdsWhenDownstreamSucceeds() {
        var client = new ResilientSimilarIdsClient(productId -> List.of("2", "3"), circuitBreaker, retry,
                degradations);

        assertThat(client.findSimilarIds("1")).containsExactly("2", "3");
    }

    @Test
    void preservesNotFoundWhenDownstreamAnswersNotFound() {
        SimilarIdsClient missing = productId -> {
            throw new ProductNotFoundException(productId);
        };
        var client = new ResilientSimilarIdsClient(missing, circuitBreaker, retry, degradations);

        assertThatThrownBy(() -> client.findSimilarIds("999"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void returnsEmptyWhenDownstreamFailsWithServerError() {
        SimilarIdsClient failing = productId -> {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        };
        var client = new ResilientSimilarIdsClient(failing, circuitBreaker, retry, degradations);

        assertThat(client.findSimilarIds("1")).isEmpty();
    }

    @Test
    void doesNotOpenTheCircuitOnRepeatedMissingProducts() {
        AtomicInteger attempts = new AtomicInteger();
        SimilarIdsClient missing = productId -> {
            attempts.incrementAndGet();
            throw new ProductNotFoundException(productId);
        };
        var client = new ResilientSimilarIdsClient(missing, circuitBreaker, retry, degradations);

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> client.findSimilarIds("999"))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(attempts.get()).isEqualTo(10);
    }

    @Test
    void opensTheCircuitAfterRepeatedServerErrors() {
        AtomicInteger attempts = new AtomicInteger();
        SimilarIdsClient failing = productId -> {
            attempts.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        };
        var client = new ResilientSimilarIdsClient(failing, circuitBreaker, retry, degradations);

        for (int i = 0; i < 4; i++) {
            assertThat(client.findSimilarIds("1")).isEmpty();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int attemptsWhenOpen = attempts.get();
        assertThat(client.findSimilarIds("1")).isEmpty();

        assertThat(attempts.get()).isEqualTo(attemptsWhenOpen);
    }

    @Test
    void countsOneDegradationWhenDownstreamIsUnavailable() {
        SimilarIdsClient failing = productId -> {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        };
        var client = new ResilientSimilarIdsClient(failing, circuitBreaker, retry, degradations);

        client.findSimilarIds("1");

        assertThat(degradations.count()).isEqualTo(1.0d);
    }

    @Test
    void countsNoDegradationForAProductThatSimplyDoesNotExist() {
        SimilarIdsClient missing = productId -> {
            throw new ProductNotFoundException(productId);
        };
        var client = new ResilientSimilarIdsClient(missing, circuitBreaker, retry, degradations);

        assertThatThrownBy(() -> client.findSimilarIds("999"))
                .isInstanceOf(ProductNotFoundException.class);

        assertThat(degradations.count()).isZero();
    }
}