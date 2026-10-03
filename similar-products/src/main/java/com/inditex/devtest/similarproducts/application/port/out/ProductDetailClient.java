package com.inditex.devtest.similarproducts.application.port.out;

import java.util.Optional;

import com.inditex.devtest.similarproducts.domain.ProductDetail;

public interface ProductDetailClient {

    /**
     * @return empty if the product does not exist (downstream 404); downstream
     *         failures throw
     */
    Optional<ProductDetail> findById(String productId);
}
