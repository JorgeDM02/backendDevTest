package com.inditex.devtest.similarproducts.support;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;

public class StubProductDetailClient implements ProductDetailClient {

    private final Map<String, ProductDetail> detailsById;
    private final Set<String> requestedIds = new HashSet<>();

    public StubProductDetailClient(Map<String, ProductDetail> detailsById) {
        this.detailsById = Map.copyOf(detailsById);
    }

    @Override
    public Optional<ProductDetail> findById(String productId) {
        requestedIds.add(productId);
        return Optional.ofNullable(detailsById.get(productId));
    }

    public Set<String> requestedIds() {
        return Set.copyOf(requestedIds);
    }
}