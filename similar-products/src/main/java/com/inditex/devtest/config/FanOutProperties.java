package com.inditex.devtest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("similar-products.fan-out")
public record FanOutProperties(int maxConcurrentCalls) {
}