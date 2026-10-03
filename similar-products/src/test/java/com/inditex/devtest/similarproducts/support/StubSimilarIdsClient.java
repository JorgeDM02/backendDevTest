package com.inditex.devtest.similarproducts.support;

import java.util.List;
import java.util.Map;

import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;

public class StubSimilarIdsClient implements SimilarIdsClient {

    private final Map<String, List<String>> idsByProductId;

    public StubSimilarIdsClient(Map<String, List<String>> idsByProductId) {
        this.idsByProductId = Map.copyOf(idsByProductId);
    }

    @Override
    public List<String> findSimilarIds(String productId) {
        List<String> ids = idsByProductId.get(productId);
        if (ids == null) {
            throw new ProductNotFoundException(productId);
        }
        return ids;
    }
}