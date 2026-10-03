package com.inditex.devtest.similarproducts.application.port.out;

import java.util.List;

import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;

public interface SimilarIdsClient {

    /**
     * @throws ProductNotFoundException if no similarity data exists for the given
     *                                  product
     */
    List<String> findSimilarIds(String productId);

}
