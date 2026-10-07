package de.agiehl.bgoffers.pricecomparison;

import com.sun.net.httpserver.HttpServer;
import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.SourceAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceComparisonHttpClientTest {

    private static final String FOUND = """
            {"status":"FOUND","name":"Scythe","normalizedName":"Scythe",
             "requestedBggId":169786,"matchedBggId":169786,
             "url":"https://www.brettspiel-angebote.de/spiele/scythe/100/","currency":"EUR",
             "availablePrice":44.90,"bestPrice":32.50,"complete":true,
             "dataSource":"LIVE","stale":false,"fetchedAt":"2026-10-07T10:00:00Z",
             "expiresAt":"2026-11-07T10:00:00Z","lastAttemptAt":"2026-10-07T09:59:59Z",
             "fallbackReason":null,"errorCode":null,"retryAt":null}
            """;

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<URI> requestUri = new AtomicReference<>();
    private final AtomicReference<String> accept = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.close();
    }

    @Test
    void callsOnlyTheApiWithUtf8EncodedNameAndOptionalBggId() {
        respond(_ -> 200, FOUND);

        var result = client().lookup("Die Glasstraße & mehr + Spaß?", 169786);

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.url()).isEqualTo("https://www.brettspiel-angebote.de/spiele/scythe/100/");
        assertThat(result.availablePrice()).isEqualByComparingTo("44.90");
        assertThat(result.bestPrice()).isEqualByComparingTo("32.50");
        assertThat(requestUri.get().toString()).isEqualTo(
                "/api/v1/prices?name=Die+Glasstra%C3%9Fe+%26+mehr+%2B+Spa%C3%9F%3F&bggId=169786");
        assertThat(accept.get()).isEqualTo("application/json");
        assertThat(requests).hasValue(1);
    }

    @Test
    void omitsUnknownBggIdAndAcceptsConfiguredBasePathWithTrailingSlash() {
        respond(_ -> 200, FOUND);

        client("/service/", Duration.ofSeconds(2)).lookup("Scythe", null);

        assertThat(requestUri.get().toString()).isEqualTo("/service/api/v1/prices?name=Scythe");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_FOUND", "SKIPPED", "ERROR"})
    void mapsApiStatusesWithoutKeepingPrices(String status) {
        respond(_ -> 200, FOUND.replace("\"FOUND\"", "\"" + status + "\""));

        assertThat(client().lookup("Scythe", null)).isEqualTo(
                PriceComparisonResult.withStatus(LookupStatus.valueOf(status)));
        assertThat(requests).hasValue(1);
    }

    @Test
    void acceptsCacheFallbackPrices() {
        respond(_ -> 200, FOUND.replace("\"LIVE\"", "\"CACHE\"")
                .replace("\"stale\":false", "\"stale\":true")
                .replace("\"fallbackReason\":null", "\"fallbackReason\":\"UPSTREAM_BLOCKED\""));

        var result = client().lookup("Scythe", 169786);

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.availablePrice()).isEqualByComparingTo("44.90");
        assertThat(result.bestPrice()).isEqualByComparingTo("32.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"availablePrice", "bestPrice"})
    void preservesPartialPriceResults(String missingPrice) {
        var body = FOUND.replace("\"complete\":true", "\"complete\":false")
                .replaceAll("\"" + missingPrice + "\":[0-9.]+", "\"" + missingPrice + "\":null");
        respond(_ -> 200, body);

        var result = client().lookup("Scythe", null);

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        if (missingPrice.equals("availablePrice")) {
            assertThat(result.availablePrice()).isNull();
            assertThat(result.bestPrice()).isEqualByComparingTo("32.50");
        } else {
            assertThat(result.availablePrice()).isEqualByComparingTo("44.90");
            assertThat(result.bestPrice()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void retriesTemporaryHttpFailuresAndReturnsRecoveredPrices(int status) {
        respond(attempt -> attempt < 3 ? status : 200, FOUND);

        assertThat(client().lookup("Scythe", null).status()).isEqualTo(LookupStatus.FOUND);
        assertThat(requests).hasValue(3);
    }

    @Test
    void stopsAfterConfiguredNumberOfHttpAttempts() {
        respond(_ -> 503, "{\"status\":\"ERROR\",\"errorCode\":\"UPSTREAM_BLOCKED\"}");

        assertThatThrownBy(() -> client().lookup("Scythe", null))
                .isInstanceOf(SourceAccessException.class).hasMessageContaining("HTTP 503");
        assertThat(requests).hasValue(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 403, 404})
    void doesNotRetryPermanentFailuresOrFollowRedirects(int status) {
        respond(_ -> status, "{}");

        assertThatThrownBy(() -> client().lookup("Scythe", null))
                .isInstanceOf(SourceAccessException.class).hasMessageContaining("HTTP " + status);
        assertThat(requests).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "not JSON", "{\"status\":\"UNKNOWN\"}"})
    void rejectsMalformedOrUnknownResponses(String body) {
        respond(_ -> 200, body);

        assertThatThrownBy(() -> client().lookup("Scythe", null)).isInstanceOf(SourceAccessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"matchedBggId\":999", "\"matchedBggId\":null",
            "\"currency\":\"USD\"", "\"availablePrice\":-1"
    })
    void rejectsInvalidPricesAndUnconfirmedIdentity(String replacement) {
        var field = replacement.substring(0, replacement.indexOf(':') + 1);
        var body = FOUND.replaceAll(field + "(?:\"[^\"]*\"|[0-9.]+)", replacement);
        respond(_ -> 200, body);

        assertThatThrownBy(() -> client().lookup("Scythe", 169786))
                .isInstanceOf(SourceAccessException.class).hasMessageContaining("ungültige Preise");
    }

    @Test
    void retriesNetworkTimeouts() {
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try {
                Thread.sleep(Duration.ofMillis(250));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        assertThatThrownBy(() -> client("", Duration.ofMillis(100)).lookup("Scythe", null))
                .isInstanceOf(SourceAccessException.class).hasMessageContaining("fehlgeschlagen");
        assertThat(requests).hasValue(3);
    }

    private PriceComparisonHttpClient client() {
        return client("", Duration.ofSeconds(2));
    }

    private PriceComparisonHttpClient client(String basePath, Duration timeout) {
        var defaults = TestProperties.create();
        var sources = defaults.sources();
        var properties = new OfferProperties(
                new OfferProperties.Sources(sources.spieleOffensive(), sources.milan(), sources.unknowns(),
                        sources.unknownsLogin(), sources.unknownsUsername(), sources.unknownsPassword(),
                        sources.bggMarket(), URI.create("http://localhost:" + server.getAddress().getPort() + basePath)),
                defaults.http(), defaults.schedule(), defaults.sourceHealth(), defaults.initialImport(),
                defaults.commitId(), defaults.telegram(), defaults.bgg());
        return new PriceComparisonHttpClient(properties, timeout);
    }

    private void respond(IntFunction<Integer> status, String body) {
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI());
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            var attempt = requests.incrementAndGet();
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Location", "https://www.brettspiel-angebote.de/");
            exchange.sendResponseHeaders(status.apply(attempt), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
