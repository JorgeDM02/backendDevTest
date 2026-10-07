package com.inditex.devtest.similarproducts.infrastructure.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.inditex.devtest.config.ResiliencePolicy;
import com.inditex.devtest.config.ResilienceProperties;
import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;
import com.inditex.devtest.similarproducts.support.TestFixtures;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ResilientProductDetailClientTest {

    private static final String DEGRADATIONS = "similar.products.downstream.degradations";

    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private Counter degradations;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("test-product-detail", ResiliencePolicy.circuitBreakerConfig(
                new ResilienceProperties.CircuitBreakerSettings(50f, 4, 4, Duration.ofSeconds(10))));
        retry = Retry.of("test-product-detail", ResiliencePolicy.retryConfig(
                new ResilienceProperties.RetrySettings(2, Duration.ZERO)));
        degradations = Counter.builder(DEGRADATIONS).register(new SimpleMeterRegistry());
    }

    @Test
    void returnsDetailWhenDownstreamSucceeds() {
        var client = new ResilientProductDetailClient(
                productId -> Optional.of(TestFixtures.product(productId)), circuitBreaker, retry, degradations);

        Optional<ProductDetail> detail = client.findById("1");

        assertThat(detail).contains(TestFixtures.product("1"));
    }

    @Test
    void returnsEmptyWhenDownstreamFailsWithServerError() {
        ProductDetailClient failing = productId -> {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        };
        var client = new ResilientProductDetailClient(failing, circuitBreaker, retry, degradations);

        assertThat(client.findById("1")).isEmpty();
    }

    @Test
    void retriesOnceOnServerErrorThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        ProductDetailClient flaky = productId -> {
            if (attempts.incrementAndGet() == 1) {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return Optional.of(TestFixtures.product(productId));
        };
        var client = new ResilientProductDetailClient(flaky, circuitBreaker, retry, degradations);

        assertThat(client.findById("1")).contains(TestFixtures.product("1"));
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void doesNotRetryOnTimeout() {
        AtomicInteger attempts = new AtomicInteger();
        ProductDetailClient timingOut = productId -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("read timed out");
        };
        var client = new ResilientProductDetailClient(timingOut, circuitBreaker, retry, degradations);

        assertThat(client.findById("1")).isEmpty();
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void opensTheCircuitAndThenFailsFastWithoutCallingDownstream() {
        AtomicInteger attempts = new AtomicInteger();
        ProductDetailClient failing = productId -> {
            attempts.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        };
        var client = new ResilientProductDetailClient(failing, circuitBreaker, retry, degradations);

        for (int i = 0; i < 4; i++) {
            assertThat(client.findById("1")).isEmpty();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int attemptsWhenOpen = attempts.get();
        assertThat(client.findById("1")).isEmpty();

        assertThat(attempts.get()).isEqualTo(attemptsWhenOpen);
    }

    @Test
    void countsOneDegradationWhenDownstreamIsUnavailable() {
        ProductDetailClient failing = productId -> {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        };
        var client = new ResilientProductDetailClient(failing, circuitBreaker, retry, degradations);

        client.findById("1");

        assertThat(degradations.count()).isEqualTo(1.0d);
    }

    @Test
    void countsNoDegradationWhenDownstreamSucceeds() {
        var client = new ResilientProductDetailClient(
                productId -> Optional.of(TestFixtures.product(productId)), circuitBreaker, retry, degradations);

        client.findById("1");

        assertThat(degradations.count()).isZero();
    }
}