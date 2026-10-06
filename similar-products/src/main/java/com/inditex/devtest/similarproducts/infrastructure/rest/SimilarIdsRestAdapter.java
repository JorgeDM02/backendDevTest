package com.inditex.devtest.similarproducts.infrastructure.rest;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;

public class SimilarIdsRestAdapter implements SimilarIdsClient {

    private static final ParameterizedTypeReference<List<String>> SIMILAR_IDS_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public SimilarIdsRestAdapter(RestClient downstreamRestClient) {
        this.restClient = downstreamRestClient;
    }

    @Override
    public List<String> findSimilarIds(String productId) {
        try {
            List<String> similarIds = restClient.get()
                    .uri("/product/{productId}/similarids", productId)
                    .retrieve()
                    .body(SIMILAR_IDS_TYPE);
            return similarIds != null ? similarIds : List.of();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId, e);
        }
    }
}