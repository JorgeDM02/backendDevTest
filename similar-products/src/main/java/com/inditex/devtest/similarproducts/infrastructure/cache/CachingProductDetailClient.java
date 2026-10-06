package com.inditex.devtest.similarproducts.infrastructure.cache;

import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;

public class CachingProductDetailClient implements ProductDetailClient {

    private final ProductDetailClient delegate;
    private final Cache<String, Optional<ProductDetail>> cache;

    public CachingProductDetailClient(ProductDetailClient delegate,
            Cache<String, Optional<ProductDetail>> cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Optional<ProductDetail> findById(String productId) {
        return cache.get(productId, delegate::findById);
    }
}