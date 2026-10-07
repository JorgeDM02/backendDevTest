package com.inditex.devtest.similarproducts.infrastructure.resilience;

import java.util.List;
import java.util.function.Supplier;

import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;

public class ResilientSimilarIdsClient implements SimilarIdsClient {

    private final SimilarIdsClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Counter degradations;

    public ResilientSimilarIdsClient(SimilarIdsClient delegate, CircuitBreaker circuitBreaker, Retry retry,
            Counter degradations) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.degradations = degradations;
    }

    @Override
    public List<String> findSimilarIds(String productId) {
        Supplier<List<String>> retryingLookup =
                Retry.decorateSupplier(retry, () -> delegate.findSimilarIds(productId));
        Supplier<List<String>> guardedLookup =
                CircuitBreaker.decorateSupplier(circuitBreaker, retryingLookup);
        try {
            return guardedLookup.get();
        } catch (ProductNotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            degradations.increment();
            return List.of();
        }
    }
}