package com.inditex.devtest.similarproducts.support;

import com.inditex.devtest.similarproducts.domain.ProductDetail;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static ProductDetail product(String id) {
        return new ProductDetail(id, "Product " + id, 9.99, true);
    }
}