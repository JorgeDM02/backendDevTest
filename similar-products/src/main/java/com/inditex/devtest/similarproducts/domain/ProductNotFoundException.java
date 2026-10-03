package com.inditex.devtest.similarproducts.domain;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productId) {
        super("Product with id " + productId + " not found");
    }

    public ProductNotFoundException(String productId, Throwable cause) {
        super("Product with id " + productId + " not found", cause);
    }
}
