package com.inditex.devtest.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("similar-products.cache")
public record CacheProperties(Duration similarIdsTtl, Duration productDetailTtl, long maximumSize) {
}