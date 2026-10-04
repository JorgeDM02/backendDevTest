package com.inditex.devtest.similarproducts.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inditex.devtest.similarproducts.application.GetSimilarProductsUseCase;

@RestController
@RequestMapping("/product")
class SimilarProductsController {

    private final GetSimilarProductsUseCase getSimilarProductsUseCase;

    SimilarProductsController(GetSimilarProductsUseCase getSimilarProductsUseCase) {
        this.getSimilarProductsUseCase = getSimilarProductsUseCase;
    }

    @GetMapping("/{productId}/similar")
    List<ProductDetailResponse> getSimilar(@PathVariable String productId) {
        return getSimilarProductsUseCase.getSimilar(productId).stream()
                .map(ProductDetailResponse::from)
                .toList();
    }
}