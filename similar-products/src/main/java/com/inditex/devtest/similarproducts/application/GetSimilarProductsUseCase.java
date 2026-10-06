package com.inditex.devtest.similarproducts.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;

@Service
public class GetSimilarProductsUseCase {

    private final SimilarIdsClient similarIdsClient;
    private final ProductDetailClient productDetailClient;
    private final Executor productDetailExecutor;

    GetSimilarProductsUseCase(SimilarIdsClient similarIdsClient,
            ProductDetailClient productDetailClient,
            @Qualifier("productDetailExecutor") Executor productDetailExecutor) {
        this.similarIdsClient = similarIdsClient;
        this.productDetailClient = productDetailClient;
        this.productDetailExecutor = productDetailExecutor;
    }

    public List<ProductDetail> getSimilar(String productId) {
        return fetchDetailsConcurrently(similarIdsClient.findSimilarIds(productId));
    }

    private List<ProductDetail> fetchDetailsConcurrently(List<String> similarIds) {
        List<CompletableFuture<Optional<ProductDetail>>> pendingLookups = new ArrayList<>(similarIds.size());
        for (String similarId : similarIds) {
            pendingLookups.add(fetchDetailAsync(similarId));
        }
        return pendingLookups.stream()
                .map(GetSimilarProductsUseCase::await)
                .flatMap(Optional::stream)
                .toList();
    }

    private CompletableFuture<Optional<ProductDetail>> fetchDetailAsync(String similarId) {
        return CompletableFuture.supplyAsync(
                () -> productDetailClient.findById(similarId), productDetailExecutor);
    }

    private static Optional<ProductDetail> await(CompletableFuture<Optional<ProductDetail>> pendingLookup) {
        try {
            return pendingLookup.join();
        } catch (CompletionException e) {
            throw propagate(e.getCause());
        }
    }

    private static RuntimeException propagate(Throwable cause) {
        return cause instanceof RuntimeException runtimeException ? runtimeException
                : new IllegalStateException(cause);
    }
}
