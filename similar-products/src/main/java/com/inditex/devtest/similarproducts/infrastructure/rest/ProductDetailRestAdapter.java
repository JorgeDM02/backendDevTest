package com.inditex.devtest.similarproducts.infrastructure.rest;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;

@Component
class ProductDetailRestAdapter implements ProductDetailClient {

    private final RestClient restClient;

    ProductDetailRestAdapter(RestClient downstreamRestClient) {
        this.restClient = downstreamRestClient;
    }

    @Override
    public Optional<ProductDetail> findById(String productId) {
        try {
            ProductDetailResponse response = restClient.get()
                    .uri("/product/{productId}", productId)
                    .retrieve()
                    .body(ProductDetailResponse.class);
            return Optional.ofNullable(response).map(ProductDetailResponse::toDomain);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}