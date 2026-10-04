package com.inditex.devtest.similarproducts.infrastructure.rest;

import com.inditex.devtest.similarproducts.domain.ProductDetail;

record ProductDetailResponse(String id, String name, double price, boolean availability) {

    ProductDetail toDomain() {
        return new ProductDetail(id, name, price, availability);
    }
}