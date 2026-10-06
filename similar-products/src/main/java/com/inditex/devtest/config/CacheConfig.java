package com.inditex.devtest.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

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
import com.inditex.devtest.similarproducts.infrastructure.rest.ProductDetailRestAdapter;
import com.inditex.devtest.similarproducts.infrastructure.rest.SimilarIdsRestAdapter;

@Configuration
public class CacheConfig {

    @Bean
    SimilarIdsClient similarIdsClient(RestClient downstreamRestClient, Cache<String, List<String>> similarIdsCache) {
        return new CachingSimilarIdsClient(new SimilarIdsRestAdapter(downstreamRestClient), similarIdsCache);
    }

    @Bean
    ProductDetailClient productDetailClient(RestClient downstreamRestClient,
            Cache<String, Optional<ProductDetail>> productDetailCache) {
        return new CachingProductDetailClient(new ProductDetailRestAdapter(downstreamRestClient),
                productDetailCache);
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