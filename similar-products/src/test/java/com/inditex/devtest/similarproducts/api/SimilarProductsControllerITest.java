package com.inditex.devtest.similarproducts.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsControllerITest {

    private static final WireMockServer DOWNSTREAM = new WireMockServer(options().dynamicPort());

    static {
        DOWNSTREAM.start();
    }

    @LocalServerPort
    private int port;

    private RestTestClient app;

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop();
    }

    @BeforeEach
    void setUp() {
        DOWNSTREAM.resetAll();
        WireMock.configureFor(DOWNSTREAM.port());
        app = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void downstreamBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("similar-products.downstream.base-url", DOWNSTREAM::baseUrl);
    }

    @Test
    void returnsSimilarProductsOrderedBySimilarity() {
        stubFor(get(urlPathEqualTo("/product/1/similarids")).willReturn(okJson("[\"2\",\"3\"]")));
        stubFor(get(urlPathEqualTo("/product/2"))
                .willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));
        stubFor(get(urlPathEqualTo("/product/3"))
                .willReturn(okJson("{\"id\":\"3\",\"name\":\"Blazer\",\"price\":29.99,\"availability\":false}")));

        app.get()
                .uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[1].id").isEqualTo("3")
                .jsonPath("$[0].price").isEqualTo(19.99)
                .jsonPath("$[1].availability").isEqualTo(false);
    }

    @Test
    void returnsPartialListWhenOneSimilarProductDoesNotExist() {
        stubFor(get(urlPathEqualTo("/product/4/similarids")).willReturn(okJson("[\"1\",\"2\",\"5\"]")));
        stubFor(get(urlPathEqualTo("/product/1"))
                .willReturn(okJson("{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")));
        stubFor(get(urlPathEqualTo("/product/2"))
                .willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));

        app.get()
                .uri("/product/4/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("1")
                .jsonPath("$[1].id").isEqualTo("2");
    }

    @Test
    void returnsEmptyListWhenThereAreNoSimilarProducts() {
        stubFor(get(urlPathEqualTo("/product/8/similarids")).willReturn(okJson("[]")));

        app.get()
                .uri("/product/8/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void returnsNotFoundWhenTheRequestedProductDoesNotExist() {
        stubFor(get(urlPathEqualTo("/product/999/similarids")).willReturn(aResponse().withStatus(404)));

        app.get()
                .uri("/product/999/similar")
                .exchange()
                .expectStatus().isNotFound();
    }
}
