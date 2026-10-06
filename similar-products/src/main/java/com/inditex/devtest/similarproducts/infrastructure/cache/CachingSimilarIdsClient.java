package com.inditex.devtest.similarproducts.infrastructure.cache;

import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;

public class CachingSimilarIdsClient implements SimilarIdsClient {

    private final SimilarIdsClient delegate;
    private final Cache<String, List<String>> cache;

    public CachingSimilarIdsClient(SimilarIdsClient delegate, Cache<String, List<String>> cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public List<String> findSimilarIds(String productId) {
        return cache.get(productId, delegate::findSimilarIds);
    }
}