package com.inditex.devtest.similarproducts.infrastructure.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.inditex.devtest.similarproducts.domain.ProductDetail;
import com.inditex.devtest.similarproducts.support.RestClients;

@WireMockTest(httpsEnabled = false)
class ProductDetailRestAdapterTest {

    private ProductDetailRestAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        adapter = new ProductDetailRestAdapter(
                RestClients.forBaseUrl(wm.getHttpBaseUrl(), Duration.ofMillis(500)));
    }

    @Test
    void returnsDetailOnOk() {
        stubFor(get(urlPathEqualTo("/product/1")).willReturn(okJson("""
                {"id":"1","name":"Shirt","price":9.99,"availability":true}
                """)));

        Optional<ProductDetail> detail = adapter.findById("1");

        assertThat(detail).contains(new ProductDetail("1", "Shirt", 9.99, true));
    }

    @Test
    void returnsEmptyWhenTheProductDoesNotExist() {
        stubFor(get(urlPathEqualTo("/product/5")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Product not found\"}")));

        assertThat(adapter.findById("5")).isEmpty();
    }

    @Test
    void propagatesServerErrorsSoResilienceCanDegrade() {
        stubFor(get(urlPathEqualTo("/product/6")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.findById("6"))
                .isInstanceOf(HttpServerErrorException.class);
    }
}