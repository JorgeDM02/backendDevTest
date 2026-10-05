package com.inditex.devtest.similarproducts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.inditex.devtest.similarproducts.application.port.out.ProductDetailClient;
import com.inditex.devtest.similarproducts.domain.ProductDetail;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;
import com.inditex.devtest.similarproducts.support.ControllableProductDetailClient;
import com.inditex.devtest.similarproducts.support.StubProductDetailClient;
import com.inditex.devtest.similarproducts.support.StubSimilarIdsClient;
import com.inditex.devtest.similarproducts.support.TestFixtures;

class GetSimilarProductsUseCaseTest {
    private ExecutorService executor;

    @BeforeEach
    void setUpExecutor() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void returnsDetailsInTheSameOrderAsTheSimilarIds() {
        var similarIds = new StubSimilarIdsClient(Map.of("1", List.of("3", "1", "2")));
        var details = new StubProductDetailClient(Map.of(
                "1", TestFixtures.product("1"),
                "2", TestFixtures.product("2"),
                "3", TestFixtures.product("3")));
        var useCase = new GetSimilarProductsUseCase(similarIds, details, executor);

        List<ProductDetail> result = useCase.getSimilar("1");

        assertThat(result).extracting(ProductDetail::id).containsExactly("3", "1", "2");
    }

    @Test
    void returnsEmptyListWhenThereAreNoSimilarProducts() {
        var useCase = new GetSimilarProductsUseCase(
                new StubSimilarIdsClient(Map.of("1", List.of())),
                new StubProductDetailClient(Map.of()), executor);

        assertThat(useCase.getSimilar("1")).isEmpty();
    }

    @Test
    void dropsSimilarProductsThatDoNotExist() {
        var similarIds = new StubSimilarIdsClient(Map.of("4", List.of("1", "2", "5")));
        var details = new StubProductDetailClient(Map.of(
                "1", TestFixtures.product("1"),
                "2", TestFixtures.product("2")));
        var useCase = new GetSimilarProductsUseCase(similarIds, details, executor);

        List<ProductDetail> result = useCase.getSimilar("4");

        assertThat(result).extracting(ProductDetail::id).containsExactly("1", "2");
    }

    @Test
    void throwsProductNotFoundWhenTheRequestedProductHasNoSimilarProducts() {
        var useCase = new GetSimilarProductsUseCase(
                new StubSimilarIdsClient(Map.of()),
                new StubProductDetailClient(Map.of()), executor);

        assertThatThrownBy(() -> useCase.getSimilar("404"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void doesNotFetchTheRequestedProductItself() {
        var similarIds = new StubSimilarIdsClient(Map.of("1", List.of("2", "3")));
        var details = new StubProductDetailClient(Map.of(
                "2", TestFixtures.product("2"),
                "3", TestFixtures.product("3")));
        var useCase = new GetSimilarProductsUseCase(similarIds, details, executor);

        useCase.getSimilar("1");

        assertThat(details.requestedIds()).containsExactlyInAnyOrder("2", "3");
    }

    @Test
    void preservesOrderWhenCallsCompleteOutOfOrder() {
        var similarIds = new StubSimilarIdsClient(Map.of("1", List.of("1", "2", "3")));
        var details = new ControllableProductDetailClient(
                Map.of("1", TestFixtures.product("1"),
                        "2", TestFixtures.product("2"),
                        "3", TestFixtures.product("3")),
                Map.of("1", 300L, "2", 150L, "3", 0L));
        var useCase = new GetSimilarProductsUseCase(similarIds, details, executor);

        List<ProductDetail> result = useCase.getSimilar("1");

        assertThat(result).extracting(ProductDetail::id).containsExactly("1", "2", "3");
    }

    @Test
    void fetchesDetailsConcurrently() {
        var similarIds = new StubSimilarIdsClient(Map.of("1", List.of("1", "2", "3")));
        var details = new ControllableProductDetailClient(
                Map.of("1", TestFixtures.product("1"),
                        "2", TestFixtures.product("2"),
                        "3", TestFixtures.product("3")),
                Map.of("1", 200L, "2", 200L, "3", 200L));
        var useCase = new GetSimilarProductsUseCase(similarIds, details, executor);

        useCase.getSimilar("1");

        assertThat(details.peakInFlight()).isEqualTo(3);
    }

    @Test
    void propagatesTheOriginalFailureInsteadOfWrappingIt() {
        var similarIds = new StubSimilarIdsClient(Map.of("1", List.of("2")));
        ProductDetailClient failing = productId -> {
            throw new IllegalStateException("downstream unavailable");
        };
        var useCase = new GetSimilarProductsUseCase(similarIds, failing, Runnable::run);

        assertThatThrownBy(() -> useCase.getSimilar("1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream unavailable");
    }
}