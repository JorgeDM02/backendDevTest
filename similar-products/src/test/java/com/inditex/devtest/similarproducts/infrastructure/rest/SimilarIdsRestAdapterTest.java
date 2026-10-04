package com.inditex.devtest.similarproducts.infrastructure.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.inditex.devtest.similarproducts.domain.ProductNotFoundException;
import com.inditex.devtest.similarproducts.support.RestClients;

@WireMockTest(httpsEnabled = false)
class SimilarIdsRestAdapterTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private SimilarIdsRestAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        adapter = new SimilarIdsRestAdapter(RestClients.forBaseUrl(wm.getHttpBaseUrl(), TIMEOUT));
    }

    @Test
    void returnsSimilarIdsOnOk() {
        stubFor(get(urlPathEqualTo("/product/1/similarids"))
                .willReturn(okJson("[\"2\",\"3\",\"4\"]")));

        assertThat(adapter.findSimilarIds("1")).containsExactly("2", "3", "4");
    }

    @Test
    void callsTheSimilarIdsEndpointOfTheRequestedProduct() {
        stubFor(get(urlPathEqualTo("/product/42/similarids"))
                .willReturn(okJson("[]")));

        adapter.findSimilarIds("42");

        verify(getRequestedFor(urlEqualTo("/product/42/similarids")));
    }

    @Test
    void throwsProductNotFoundOnNotFound() {
        stubFor(get(urlPathEqualTo("/product/999/similarids"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Product not found\"}")));

        assertThatThrownBy(() -> adapter.findSimilarIds("999"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void propagatesServerErrorsSoResilienceCanDegrade() {
        stubFor(get(urlPathEqualTo("/product/5/similarids"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.findSimilarIds("5"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void propagatesTimeoutsInsteadOfBlockingForever() {
        stubFor(get(urlPathEqualTo("/product/3/similarids"))
                .willReturn(okJson("[\"100\"]").withFixedDelay(2_000)));

        assertThatThrownBy(() -> adapter.findSimilarIds("3"))
                .isInstanceOf(ResourceAccessException.class);
    }
}