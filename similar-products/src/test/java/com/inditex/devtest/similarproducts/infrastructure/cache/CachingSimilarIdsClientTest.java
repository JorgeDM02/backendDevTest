package com.inditex.devtest.similarproducts.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;
import com.inditex.devtest.similarproducts.support.ControllableSimilarIdsClient;

class CachingSimilarIdsClientTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private ControllableSimilarIdsClient delegate;
    private Cache<String, List<String>> cache;
    private CachingSimilarIdsClient cachingClient;

    @BeforeEach
    void setUp() {
        delegate = new ControllableSimilarIdsClient(Map.of("1", List.of("2", "3")), Map.of());
        cache = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(TTL).build();
        cachingClient = new CachingSimilarIdsClient(delegate, cache);
    }

    @Test
    void servesRepeatedLookupsFromCache() {
        assertThat(cachingClient.findSimilarIds("1")).containsExactly("2", "3");
        assertThat(cachingClient.findSimilarIds("1")).containsExactly("2", "3");

        assertThat(delegate.invocationCount()).isEqualTo(1);
    }

    @Test
    void doesNotCacheFailures() {
        assertThatThrownBy(() -> cachingClient.findSimilarIds("999"))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> cachingClient.findSimilarIds("999"))
                .isInstanceOf(ProductNotFoundException.class);

        assertThat(delegate.invocationCount()).isEqualTo(2);
    }

    @Test
    @Timeout(10)
    void coalescesConcurrentLookupsForTheSameProduct() throws InterruptedException {
        var slowDelegate = new ControllableSimilarIdsClient(
                Map.of("1", List.of("2", "3")), Map.of("1", 150L));
        var client = new CachingSimilarIdsClient(slowDelegate, cache);
        var start = new CountDownLatch(1);
        var finished = new CountDownLatch(8);
        var threads = Executors.newFixedThreadPool(8);

        try {
            for (int i = 0; i < 8; i++) {
                threads.submit(() -> {
                    try {
                        start.await();
                        client.findSimilarIds("1");
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
        Cache<String, List<String>> tickedCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(TTL)
                .ticker(clock::get)
                .build();
        var client = new CachingSimilarIdsClient(delegate, tickedCache);

        client.findSimilarIds("1");
        clock.addAndGet(TTL.plusSeconds(1).toNanos());
        client.findSimilarIds("1");

        assertThat(delegate.invocationCount()).isEqualTo(2);
    }
}