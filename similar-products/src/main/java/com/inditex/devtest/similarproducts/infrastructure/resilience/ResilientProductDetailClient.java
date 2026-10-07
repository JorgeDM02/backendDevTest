package com.inditex.devtest.similarproducts.infrastructure.resilience;

import java.util.Optional;
import java.util.function.Supplier;

import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;

public class ResilientProductDetailClient implements ProductDetailClient {

    private final ProductDetailClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Counter degradations;

    public ResilientProductDetailClient(ProductDetailClient delegate, CircuitBreaker circuitBreaker,
            Retry retry, Counter degradations) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.degradations = degradations;
    }

    @Override
    public Optional<ProductDetail> findById(String productId) {
        Supplier<Optional<ProductDetail>> retryingLookup =
                Retry.decorateSupplier(retry, () -> delegate.findById(productId));
        Supplier<Optional<ProductDetail>> guardedLookup =
                CircuitBreaker.decorateSupplier(circuitBreaker, retryingLookup);
        try {
            return guardedLookup.get();
        } catch (RuntimeException e) {
            degradations.increment();
            return Optional.empty();
        }
    }
}