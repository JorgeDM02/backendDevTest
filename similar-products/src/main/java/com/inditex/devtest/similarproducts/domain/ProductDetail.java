package com.inditex.devtest.similarproducts.domain;

public record ProductDetail(
                String id,
                String name,
                double price,
                boolean availability) {
}