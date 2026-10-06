package com.inditex.devtest.similarproducts.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;
import com.inditex.devtest.similarproducts.support.ControllableProductDetailClient;
import com.inditex.devtest.similarproducts.support.TestFixtures;

class CachingProductDetailClientTest {

    private static final Duration TTL = Duration.ofSeconds(30);

    private ControllableProductDetailClient delegate;
    private Cache<String, Optional<ProductDetail>> cache;
    private CachingProductDetailClient cachingClient;

    @BeforeEach
    void setUp() {
        delegate = new ControllableProductDetailClient(
                Map.of("1", TestFixtures.product("1"), "2", TestFixtures.product("2")), Map.of());
        cache = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(TTL).build();
        cachingClient = new CachingProductDetailClient(delegate, cache);
    }

    @Test
    void servesRepeatedLookupsFromCache() {
        assertThat(cachingClient.findById("1")).isPresent();
        assertThat(cachingClient.findById("1")).isPresent();

        assertThat(delegate.invocationCount()).isEqualTo(1);
    }

    @Test
    void cachesMissingProducts() {
        assertThat(cachingClient.findById("5")).isEmpty();
        assertThat(cachingClient.findById("5")).isEmpty();

        assertThat(delegate.invocationCount()).isEqualTo(1);
    }

    @Test
    void doesNotCacheFailures() {
        AtomicInteger attempts = new AtomicInteger();
        ProductDetailClient failing = productId -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("downstream unavailable");
        };
        var client = new CachingProductDetailClient(failing, cache);

        assertThatThrownBy(() -> client.findById("1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.findById("1")).isInstanceOf(IllegalStateException.class);

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @Timeout(10)
    void coalescesConcurrentLookupsForTheSameProduct() throws InterruptedException {
        var slowDelegate = new ControllableProductDetailClient(
                Map.of("1", TestFixtures.product("1")), Map.of("1", 150L));
        var client = new CachingProductDetailClient(slowDelegate, cache);
        var start = new CountDownLatch(1);
        var finished = new CountDownLatch(8);
        var threads = Executors.newFixedThreadPool(8);

        try {
            for (int i = 0; i < 8; i++) {
                threads.submit(() -> {
                    try {
                        start.await();
                        client.findById("1");
                        finished.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();

            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            threads.shutdownNow();
        }

        assertThat(slowDelegate.invocationCount()).isEqualTo(1);
    }

    @Test
    void expiresEntriesAfterTheConfiguredTtl() {
        var clock = new AtomicLong();
        Cache<String, Optional<ProductDetail>> tickedCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(TTL)
                .ticker(clock::get)
                .build();
        var client = new CachingProductDetailClient(delegate, tickedCache);

        client.findById("1");
        clock.addAndGet(TTL.plusSeconds(1).toNanos());
        client.findById("1");

        assertThat(delegate.invocationCount()).isEqualTo(2);
    }
}