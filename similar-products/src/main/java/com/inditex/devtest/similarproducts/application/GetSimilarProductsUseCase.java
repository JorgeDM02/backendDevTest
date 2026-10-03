package com.inditex.devtest.similarproducts.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;

@Service
public class GetSimilarProductsUseCase {

    private final SimilarIdsClient similarIdsClient;
    private final ProductDetailClient productDetailClient;

    GetSimilarProductsUseCase(SimilarIdsClient similarIdsClient, ProductDetailClient productDetailClient) {

        this.similarIdsClient = similarIdsClient;
        this.productDetailClient = productDetailClient;
    }

    public List<ProductDetail> getSimilar(String productId) {
        return similarIdsClient.findSimilarIds(productId).stream().map(productDetailClient::findById)
                .flatMap(Optional::stream).toList();
    }
}
