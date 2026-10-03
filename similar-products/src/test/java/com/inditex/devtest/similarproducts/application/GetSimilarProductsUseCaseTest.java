package com.inditex.devtest.similarproducts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.inditex.devtest.similarproducts.domain.ProductDetail;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;
import com.inditex.devtest.similarproducts.support.StubProductDetailClient;
import com.inditex.devtest.similarproducts.support.StubSimilarIdsClient;
import com.inditex.devtest.similarproducts.support.TestFixtures;

class GetSimilarProductsUseCaseTest {

    @Test
    void returnsDetailsInTheSameOrderAsTheSimilarIds() {
        var similarIds = new StubSimilarIdsClient(Map.of("1", List.of("3", "1", "2")));
        var details = new StubProductDetailClient(Map.of(
                "1", TestFixtures.product("1"),
                "2", TestFixtures.product("2"),
                "3", TestFixtures.product("3")));
        var useCase = new GetSimilarProductsUseCase(similarIds, details);

        List<ProductDetail> result = useCase.getSimilar("1");

        assertThat(result).extracting(ProductDetail::id).containsExactly("3", "1", "2");
    }

    @Test
    void returnsEmptyListWhenThereAreNoSimilarProducts() {
        var useCase = new GetSimilarProductsUseCase(
                new StubSimilarIdsClient(Map.of("1", List.of())),
                new StubProductDetailClient(Map.of()));

        assertThat(useCase.getSimilar("1")).isEmpty();
    }

    @Test
    void dropsSimilarProductsThatDoNotExist() {
        var similarIds = new StubSimilarIdsClient(Map.of("4", List.of("1", "2", "5")));
        var details = new StubProductDetailClient(Map.of(
                "1", TestFixtures.product("1"),
                "2", TestFixtures.product("2")));
        var useCase = new GetSimilarProductsUseCase(similarIds, details);

        List<ProductDetail> result = useCase.getSimilar("4");

        assertThat(result).extracting(ProductDetail::id).containsExactly("1", "2");
    }

    @Test
    void throwsProductNotFoundWhenTheRequestedProductHasNoSimilarProducts() {
        var useCase = new GetSimilarProductsUseCase(
                new StubSimilarIdsClient(Map.of()),
                new StubProductDetailClient(Map.of()));

        assertThatThrownBy(() -> useCase.getSimilar("404"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void doesNotFetchTheRequestedProductItself() {
        var similarIds = new StubSimilarIdsClient(Map.of("1", List.of("2", "3")));
        var details = new StubProductDetailClient(Map.of(
                "2", TestFixtures.product("2"),
                "3", TestFixtures.product("3")));
        var useCase = new GetSimilarProductsUseCase(similarIds, details);

        useCase.getSimilar("1");

        assertThat(details.requestedIds()).containsExactlyInAnyOrder("2", "3");
    }
}