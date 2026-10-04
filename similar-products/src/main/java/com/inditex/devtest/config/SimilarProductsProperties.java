package com.inditex.devtest.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("similar-products.downstream")
public record SimilarProductsProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
}