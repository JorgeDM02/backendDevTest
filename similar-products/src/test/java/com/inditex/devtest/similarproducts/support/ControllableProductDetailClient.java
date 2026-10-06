package com.inditex.devtest.similarproducts.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;

public class ControllableProductDetailClient implements ProductDetailClient {

    private final Map<String, ProductDetail> detailsById;
    private final Map<String, Long> delayMillisById;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();
    private final AtomicInteger invocationCount = new AtomicInteger();

    public ControllableProductDetailClient(Map<String, ProductDetail> detailsById,
            Map<String, Long> delayMillisById) {
        this.detailsById = Map.copyOf(detailsById);
        this.delayMillisById = Map.copyOf(delayMillisById);
    }

    @Override
    public Optional<ProductDetail> findById(String productId) {
        invocationCount.incrementAndGet();
        peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        try {
            sleep(delayMillisById.getOrDefault(productId, 0L));
            return Optional.ofNullable(detailsById.get(productId));
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public int peakInFlight() {
        return peakInFlight.get();
    }

    public int invocationCount() {
        return invocationCount.get();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}