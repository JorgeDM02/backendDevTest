package com.inditex.devtest.similarproducts.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.inditex.devtest.similarproducts.application.port.out.SimilarIdsClient;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;

public class ControllableSimilarIdsClient implements SimilarIdsClient {

    private final Map<String, List<String>> idsByProductId;
    private final Map<String, Long> delayMillisById;
    private final AtomicInteger invocationCount = new AtomicInteger();

    public ControllableSimilarIdsClient(Map<String, List<String>> idsByProductId,
                                        Map<String, Long> delayMillisById) {
        this.idsByProductId = Map.copyOf(idsByProductId);
        this.delayMillisById = Map.copyOf(delayMillisById);
    }

    @Override
    public List<String> findSimilarIds(String productId) {
        invocationCount.incrementAndGet();
        sleep(delayMillisById.getOrDefault(productId, 0L));
        List<String> similarIds = idsByProductId.get(productId);
        if (similarIds == null) {
            throw new ProductNotFoundException(productId);
        }
        return similarIds;
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