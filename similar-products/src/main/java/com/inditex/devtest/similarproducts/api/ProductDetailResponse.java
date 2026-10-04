package com.inditex.devtest.similarproducts.api;

import com.inditex.devtest.similarproducts.domain.ProductDetail;

record ProductDetailResponse(String id, String name, double price, boolean availability) {

    static ProductDetailResponse from(ProductDetail productDetail) {
        return new ProductDetailResponse(productDetail.id(), productDetail.name(),
                productDetail.price(), productDetail.availability());
    }
}