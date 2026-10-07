package com.inditex.devtest.config;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;
import com.inditex.devtest.similarproducts.infrastructure.cache.CachingProductDetailClient;
import com.inditex.devtest.similarproducts.infrastructure.cache.CachingSimilarIdsClient;
import com.inditex.devtest.similarproducts.infrastructure.resilience.ResilientProductDetailClient;
import com.inditex.devtest.similarproducts.infrastructure.resilience.ResilientSimilarIdsClient;
import com.inditex.devtest.similarproducts.infrastructure.rest.ProductDetailRestAdapter;
import com.inditex.devtest.similarproducts.infrastructure.rest.SimilarIdsRestAdapter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class DownstreamClientConfig {

    @Bean
    SimilarIdsClient similarIdsClient(RestClient downstreamRestClient,
            @Qualifier("similarIdsCircuitBreaker") CircuitBreaker similarIdsCircuitBreaker,
            @Qualifier("similarIdsRetry") Retry similarIdsRetry,
            Cache<String, List<String>> similarIdsCache,
            MeterRegistry meterRegistry) {
        Counter degradations = Counter.builder(ObservabilityConfig.DEGRADATIONS)
                .tag("client", "similar-ids")
                .register(meterRegistry);
        SimilarIdsClient resilient = new ResilientSimilarIdsClient(
                new SimilarIdsRestAdapter(downstreamRestClient), similarIdsCircuitBreaker, similarIdsRetry,
                degradations);
        return new CachingSimilarIdsClient(resilient, similarIdsCache);
    }

    @Bean
    ProductDetailClient productDetailClient(RestClient downstreamRestClient,
            @Qualifier("productDetailCircuitBreaker") CircuitBreaker productDetailCircuitBreaker,
            @Qualifier("productDetailRetry") Retry productDetailRetry,
            Cache<String, Optional<ProductDetail>> productDetailCache,
            MeterRegistry meterRegistry) {
        Counter degradations = Counter.builder(ObservabilityConfig.DEGRADATIONS)
                .tag("client", "product-detail")
                .register(meterRegistry);
        ProductDetailClient resilient = new ResilientProductDetailClient(
                new ProductDetailRestAdapter(downstreamRestClient), productDetailCircuitBreaker,
                productDetailRetry, degradations);
        return new CachingProductDetailClient(resilient, productDetailCache);
    }

    @Bean
    Cache<String, List<String>> similarIdsCache(CacheProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.similarIdsTtl())
                .build();
    }

    @Bean
    Cache<String, Optional<ProductDetail>> productDetailCache(CacheProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.productDetailTtl())
                .build();
    }
}