# Similar Products API

Aggregates two upstream endpoints into a single customer-facing call:

```
GET /product/{productId}/similar
```

It takes the ids of similar products, fetches each one's detail, and returns them ordered by similarity.

**Stack:** Java 21, Spring Boot 4.1.1, Spring MVC (blocking), RestClient, Caffeine, Resilience4j, Micrometer

---

## Request lifecycle

The main design decision is where each cross-cutting concern lives. Each one is a decorator around an interface that the application layer owns, and the whole stack is assembled in a single place:

```
inbound request
   |
   |- api.SimilarProductsController            HTTP mapping only
   |    |- application.GetSimilarProductsUseCase
   |
   |- SimilarIdsClient / ProductDetailClient   (interfaces owned by application)
        |
        |- Caching*Client                      cache, TTL, single-flight
        |    |- Resilient*Client               circuit breaker -> retry
        |         |- *RestAdapter              RestClient, status mapping
        |              |- downstream HTTP
```

---

## Failure handling

The contract allows two outcomes: `200` and `404`. There is deliberately no `503` anywhere in this service. A dependency failure degrades the response instead of changing its shape.

| Upstream says | `SimilarIdsClient` | `ProductDetailClient` | HTTP result |
|---|---|---|---|
`200` | id list | detail | `200` |
`404` | throws `ProductNotFoundException` | `Optional.empty()` | `404` for ids / item silently dropped for details |
`500` | retried once then `List.of()` | retried once then `Optional.empty()` | `200`, degraded |
timeout | `List.of()` | `Optional.empty()` | `200`, degraded |
breaker open | `List.of()` | `Optional.empty()` | `200`, degraded |

Two things here are easy to get wrong:

- **The two clients are asymmetric on purpose.** On the ids port, `404` and "we are broken" are different answers. A `404` propagates, while a `500` degrades to an empty list. On the detail port the two collapse into the same thing, so a failed lookup becomes `Optional.empty()` and follows the same path a missing sibling already takes.
- **A `404` is not a failure.** The breaker config calls `.ignoreExceptions(ProductNotFoundException.class)`. Without it, the `notFound` load scenario, where a legitimate `404` arrives 200 times a second, would be recorded as dependency failure. That would trip the breaker and degrade traffic that was working perfectly.

`ProductNotFoundException` also needs its own `catch` clause ahead of the generic `RuntimeException` handler, since it extends `RuntimeException`. Swap the order and every legitimate `404` quietly becomes `200 []`.

---

## Project layout

```
src/main/java/com/inditex/devtest/
|-- DevtestApplication.java
|-- similarproducts/
|   |-- domain/            ProductDetail, ProductNotFoundException        (no framework imports)
|   |-- application/
|   |   |-- GetSimilarProductsUseCase                                     (the only business logic)
|   |   |-- port/out/       SimilarIdsClient, ProductDetailClient        (interfaces)
|   |-- infrastructure/
|   |   |-- rest/           SimilarIdsRestAdapter, ProductDetailRestAdapter, ProductDetailResponse
|   |   |-- cache/          CachingSimilarIdsClient, CachingProductDetailClient
|   |   |-- resilience/     ResilientSimilarIdsClient, ResilientProductDetailClient
|   |-- api/                SimilarProductsController, ProductDetailResponse,
|                           SimilarProductsExceptionHandler
|-- config/                 DownstreamClientConfig (composition root), ResiliencePolicy,
                            ObservabilityConfig, ExecutorConfig, HttpClientConfig,
                            SimilarProductsProperties, FanOutProperties, CacheProperties,
                            ResilienceProperties
```

`domain` and `application` import no Spring, no Jackson, and no resilience4j. That is what lets the whole contract be tested in 8 plain JUnit tests with no Spring context.

The dependency direction is inward only. `DownstreamClientConfig` is the single composition root, and it states the decorator order explicitly. Changing the order of concerns is a one-file change, with no edit to the use case, the ports, or the adapters.

---

## Running it

Java 21+, Docker with Compose.

1. From the repository root, start the mock upstream and the monitoring stack:
   ```bash
   docker-compose up -d simulado influxdb grafana
   ```

2. Confirm the mock answers:
   ```bash
   curl http://localhost:3001/product/1/similarids
   # [2,3,4]
   ```

3. Start the service:
   ```bash
   cd similar-products
   ./mvnw spring-boot:run
   ```

4. Call it:
   ```bash
   curl http://localhost:5000/product/1/similar
   ```

   ```json
   [
     {"id":"2","name":"Dress","price":19.99,"availability":true},
     {"id":"3","name":"Blazer","price":29.99,"availability":false},
     {"id":"4","name":"Boots","price":39.99,"availability":true}
   ]
   ```

---

## Observability

Enabled endpoints: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/metrics`.

Liveness does not include a downstream check. A liveness probe that depends on an upstream API turns a slow dependency into a restart loop, and restarting does not help because the thing being waited on is still slow.

Custom metrics:

| Metric | Type | Tag | Meaning |
|---|---|---|---|
`similar.products.downstream.degradations` | counter | `client` | upstream lookups abandoned because the API failed or the circuit was open |
`similar.products.circuit.breaker.state` | gauge | `name` | `0`=CLOSED, `1`=OPEN, `2`=HALF_OPEN |
`similar.products.circuit.breaker.failed.calls` | gauge | `name` | failures recorded by the breaker |
`similar.products.circuit.breaker.not.permitted.calls` | gauge | `name` | calls short-circuited while open |

The degradation counter exists because the load test's error rate goes to zero while some responses are silently partial. Counting degraded lookups makes that visible. Note that it counts upstream lookups that degraded, not responses that were partial. Negative caching means a single upstream failure affects many later requests while the counter stays flat.

A legitimate `404` is not counted as a degradation. There is a test asserting exactly that, so the signal does not drown in correct behaviour.

---

## Tests

```bash
./mvnw test
```

44 tests, no Docker required. The upstream is simulated with in-process WireMock servers.

| Suite | Count | Covers |
|---|---|---|
`GetSimilarProductsUseCaseTest` | 8 | ordering, empty results, dropped siblings, `404` propagation, fan-out concurrency |
`ResilientProductDetailClientTest` | 7 | degradation, retry, no-retry-on-timeout, breaker fail-fast, counter |
`ResilientSimilarIdsClientTest` | 7 | `404` preservation, degradation, breaker stays closed on `404`s, counter |
`CachingProductDetailClientTest` | 5 | hit/miss, negative caching, no caching of failures, single-flight, TTL |
`CachingSimilarIdsClientTest` | 4 | as above for the ids port |
`SimilarIdsRestAdapterTest` | 5 | status-code mapping, URI construction, timeout propagation |
`ProductDetailRestAdapterTest` | 3 | `200` parsing, `404` to empty, `5xx` propagation |
`SimilarProductsControllerITest` | 4 | full stack against WireMock: contract, ordering, partial results, `404` |
`DevtestApplicationTests` | 1 | context loads |

TTL tests inject Caffeine's `Ticker` and jump time forward instead of sleeping, so they are deterministic. The concurrency tests assert peak in-flight calls rather than elapsed time, so they cannot pass by luck or fail on a slow machine.

---

## Measured results

Run from the repository root, so that `docker-compose.yaml` and the `shared/` scripts resolve:

```bash
docker-compose run --rm k6 run scripts/test.js
```

Dashboard: http://localhost:3000/d/Le2Ku9NMk/k6-performance-test

200 concurrent VUs across five scenarios.

| | |
|---|---|
Requests (`http_reqs`) | 19,141 |
Iterations completed | 18,902 |
HTTP status | 19,141 requests, all `200`, no `4xx` or `5xx` |
Duration mean / max / min | 16.25ms / 616.6ms / 0.51ms |
Max VUs sustained (`vus_max`) | 200 |
Degradations | 5 |
Circuit breaker | never opened (0 not-permitted calls) |

Per scenario:

| Scenario | Product | mean | max |
|---|---|---|---|
`notFound` | 4 | 3.25ms | 19.2ms |
`error` | 5 | 5.50ms | 64.5ms |
`normal` | 1 | 10.66ms | 171.7ms |
`verySlow` | 3 | 29.48ms | 513.5ms |
`slow` | 2 | 33.95ms | 616.6ms |

### Request timing breakdown

k6 decomposes every request into phases, which is what shows where the time is spent:

| Metric | mean | max | What it measures |
|---|---|---|---|
`http_req_waiting` | 16.21ms | 616.61ms | service processing time |
`http_req_blocked` | 0.28ms | 42.44ms | waiting for a free connection |
`http_req_connecting` | 0.14ms | 33.02ms | connection establishment |
`http_req_sending` | 0.02ms | 5.05ms | writing the request |
`http_req_receiving` | 0.02ms | 2.35ms | reading the response |

`http_req_blocked` is the number that answers the thread-pool question. At 0.28ms mean and 42.44ms worst case, requests spent almost no time queued for a connection. Running 200 VUs against Tomcat's default 200 threads produced no measurable contention, so the pool size stays where it is. Almost all elapsed time is `waiting`, which is the service itself.

The 616.61ms `waiting` maximum is the same cold-path read timeout visible in the `slow` scenario's maximum. The service spent real time handling a genuinely slow upstream once. That is not queueing.

The headline is five degradations across 19,141 requests. Negative caching absorbs the failing lookups and the breaker never had to intervene.

Four of the five are directly attributable: one in `error` (product `6`), and two in `slow` (products `100` and `1000`, whose entries are then cached as absent). The fifth comes from `verySlow`. The cached absences written at the start of `slow` have a 30-second TTL that expires at roughly the same moment that scenario ends, so a late lookup in `verySlow` misses and degrades again. That is the cache behaving as configured.

Two notes on reading these numbers:

- The Grafana duration panel plots `mean`, which flattens the cold-path spike into the 16ms average. The per-scenario maxima above are the more informative figure.
- k6's `normal`, `notFound`, and `error` scenarios use `gracefulStop: 0s`, so k6 terminates their in-flight iterations when each scenario's duration ends. Interrupted-iteration messages from those three scenarios (up to 200 VUs each) are an artefact of the script, not a service error.

### Reproducing these numbers

InfluxDB accumulates results, so a second run blends into the first and the aggregates no longer describe either. The figures above were read from a single run's time window. If the dashboard looks worse than the table, or `http_req_duration` averages near zero, a run was made while the service was down and those requests recorded `status=0` in about 0ms. Drop the database and re-run for a clean set:

```bash
docker exec backenddevtest-influxdb-1 influx -d 'DROP DATABASE k6'
docker-compose run --rm k6 run scripts/test.js
```

---

## Configuration

All settings live in `application.yml` and are bound to records under `config/`.

### Downstream

| Property | Default | Description |
|---|---|---|
`similar-products.downstream.base-url` | `http://localhost:3001` | Upstream API base URL |
`similar-products.downstream.connect-timeout` | `500ms` | TCP connection timeout |
`similar-products.downstream.read-timeout` | `500ms` | Response read timeout, well below the slowest simulated upstream |

The read timeout is a correctness requirement, not tuning. The 50-second upstream would otherwise pin a Tomcat thread for 50 seconds per request, and 200 VUs would exhaust the pool in seconds.

### Fan-out

| Property | Default | Description |
|---|---|---|
`similar-products.fan-out.max-concurrent-calls` | `64` | Core equals max pool size, which is the real concurrency ceiling |

### Cache

| Property | Default | Description |
|---|---|---|
`similar-products.cache.similar-ids-ttl` | `5m` | Similarity lists are near-static |
`similar-products.cache.product-detail-ttl` | `30s` | Details carry mutable price and availability |
`similar-products.cache.maximum-size` | `10000` | Bound on each cache, to keep heap usage predictable |

### Resilience

Set per upstream API, so the two breakers are independent:

| Property | Default | Description |
|---|---|---|
`similar-products.resilience.{similar-ids,product-detail}.failure-rate-threshold` | `50` | Failure percentage that opens the circuit |
`similar-products.resilience.{similar-ids,product-detail}.minimum-number-of-calls` | `20` | Calls required before the rate is evaluated |
`similar-products.resilience.{similar-ids,product-detail}.sliding-window-size` | `100` | Calls in the evaluation window |
`similar-products.resilience.{similar-ids,product-detail}.wait-duration-in-open-state` | `10s` | Time before probing upstream again |
`similar-products.resilience.retry.max-attempts` | `2` | One retry |
`similar-products.resilience.retry.wait-duration` | `50ms` | Delay between attempts |

Retry is restricted to `5xx`. Timeouts, `404`s, and open-circuit rejections are all excluded by a single type check in `ResiliencePolicy`, because none of them benefit from another attempt. Retrying a timeout just waits a second 500ms, and retrying a rejected call defeats the breaker. Both breakers also ignore `ProductNotFoundException`.

---

## Tech stack

- Java 21, Spring Boot 4.1.1, Spring MVC, RestClient with JdkClientHttpRequestFactory
- Caffeine, in-process cache, version managed by the Spring Boot BOM
- Resilience4j 2.4.0, using the resilience4j-circuitbreaker and resilience4j-retry core modules only. The spring-boot4 starter is not used: resilience is composed programmatically, so annotation support is unnecessary.
- Micrometer via spring-boot-starter-actuator
- JUnit 5 and AssertJ
- WireMock 3.13.2 (test scope), in-process upstream simulation
- Docker Compose, k6, Grafana, InfluxDB
