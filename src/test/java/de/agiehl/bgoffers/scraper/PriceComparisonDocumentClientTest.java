package de.agiehl.bgoffers.scraper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class PriceComparisonDocumentClientTest {

    @Test
    void loadsLandingPageAndLogsEveryRequestWithHeadersAndCookies(CapturedOutput output) throws Exception {
        var landingRequests = new AtomicInteger();
        var searchCookie = new AtomicReference<String>();
        var searchReferer = new AtomicReference<String>();
        var detailCookie = new AtomicReference<String>();
        var detailReferer = new AtomicReference<String>();
        var searchFetchDestination = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/suche/", exchange -> {
            searchCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            searchReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            searchFetchDestination.set(exchange.getRequestHeaders().getFirst("Sec-Fetch-Dest"));
            exchange.getResponseHeaders().add("Set-Cookie", "search_step=redirect; Path=/; HttpOnly");
            exchange.getResponseHeaders().set("Location", "/spiele/scythe/100/");
            respondWithoutBody(exchange, 302);
        });
        server.createContext("/spiele/scythe/100/", exchange -> {
            detailCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            detailReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            respond(exchange, 200, "text/html; charset=UTF-8", """
                    <html><body><meta itemprop="lowPrice" content="44.90"></body></html>
                    """);
        });
        server.createContext("/", exchange -> {
            landingRequests.incrementAndGet();
            exchange.getResponseHeaders().add(
                    "Set-Cookie", "bunny_shield=shield-value; Path=/; HttpOnly");
            exchange.getResponseHeaders().add(
                    "Set-Cookie", "bunny_shield_id_85029=shield-id; Path=/; HttpOnly");
            respond(exchange, 200, "text/html; charset=UTF-8", "<html><body>Startseite</body></html>");
        });
        server.start();
        var httpLogger = (Logger) LoggerFactory.getLogger(PriceComparisonHttpClient.class);
        var previousLevel = httpLogger.getLevel();
        httpLogger.setLevel(Level.DEBUG);
        try {
            var baseUri = URI.create("http://localhost:%d/".formatted(server.getAddress().getPort()));
            var searchUri = baseUri.resolve("suche/?s=scythe");
            var client = new PriceComparisonDocumentClient(properties(baseUri));

            var result = client.search(searchUri);

            assertThat(result.location()).isEqualTo(baseUri.resolve("spiele/scythe/100/").toString());
            assertThat(result.selectFirst("[itemprop=lowPrice]").attr("content")).isEqualTo("44.90");
            assertThat(landingRequests).hasValue(1);
            assertThat(searchCookie.get()).contains(
                    "bunny_shield=shield-value",
                    "bunny_shield_id_85029=shield-id");
            assertThat(detailCookie.get()).contains(
                    "bunny_shield=shield-value",
                    "bunny_shield_id_85029=shield-id",
                    "search_step=redirect");
            assertThat(searchReferer).hasValue(baseUri.toString());
            assertThat(detailReferer).hasValue(searchUri.toString());
            assertThat(searchFetchDestination).hasValue("document");
            assertThat(output.getOut())
                    .contains("Brettspiel-Angebote HTTP-Request")
                    .contains("Brettspiel-Angebote HTTP-Response")
                    .contains("Methode=GET")
                    .contains("Protokoll=ausgehandelt")
                    .contains("Protokoll=HTTP/1.1")
                    .contains("Header=")
                    .contains("Cookies=<keine>")
                    .contains("bunny_shield=shield-value")
                    .contains("bunny_shield_id_85029=shield-id")
                    .contains("search_step=redirect")
                    .contains("/spiele/scythe/100/");
        } finally {
            httpLogger.setLevel(previousLevel);
            server.stop(0);
        }
    }

    @Test
    void providesExactlyOnePriceComparisonClientImplementation() {
        new ApplicationContextRunner()
                .withBean(OfferProperties.class, TestProperties::create)
                .withUserConfiguration(PriceComparisonDocumentClient.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(PriceComparisonClient.class)
                        .hasSingleBean(PriceComparisonDocumentClient.class));
    }

    @Test
    void rejectsRequestsToOtherOriginsBeforeOpeningASession() {
        var client = new PriceComparisonDocumentClient(TestProperties.create());

        assertThatThrownBy(() -> client.search(URI.create("https://example.org/")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("konfigurierten Ursprung");
    }

    @Test
    void rejectsRedirectsToOtherOrigins() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/suche/", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://example.org/spiele/scythe/");
            respondWithoutBody(exchange, 302);
        });
        server.createContext("/", exchange -> respond(
                exchange, 200, "text/html; charset=UTF-8", "<html><body>Startseite</body></html>"));
        server.start();
        try {
            var baseUri = URI.create("http://localhost:%d/".formatted(server.getAddress().getPort()));
            var client = new PriceComparisonDocumentClient(properties(baseUri));

            assertThatThrownBy(() -> client.search(baseUri.resolve("suche/?s=scythe")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("konfigurierten Ursprung");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void logsThatAForbiddenResponseOccurredWhileLoadingTheLandingPage(CapturedOutput output) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> respond(exchange, 403, "text/html", "Forbidden"));
        server.start();
        try {
            var baseUri = URI.create("http://localhost:%d/".formatted(server.getAddress().getPort()));
            var client = new PriceComparisonDocumentClient(properties(baseUri));

            assertThatThrownBy(() -> client.search(baseUri.resolve("suche/?s=scythe")))
                    .isInstanceOf(SourceAccessException.class)
                    .hasMessageContaining("HTTP 403");
            assertThat(output.getOut())
                    .contains("Typ=Startseite")
                    .contains("HTTP=403")
                    .contains("Content-Type=text/html")
                    .contains("Bytes=9");
        } finally {
            server.stop(0);
        }
    }

    private OfferProperties properties(URI priceComparisonUri) {
        var defaults = TestProperties.create();
        var sources = defaults.sources();
        return new OfferProperties(
                new OfferProperties.Sources(
                        sources.spieleOffensive(),
                        sources.milan(),
                        sources.unknowns(),
                        sources.unknownsLogin(),
                        sources.unknownsUsername(),
                        sources.unknownsPassword(),
                        sources.bggMarket(),
                        priceComparisonUri),
                new OfferProperties.Http(
                        Duration.ofSeconds(2), "test", 1, Duration.ZERO, Duration.ZERO, 1),
                defaults.schedule(),
                defaults.sourceHealth(),
                defaults.initialImport(),
                defaults.commitId(),
                defaults.telegram(),
                defaults.bgg());
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        var response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static void respondWithoutBody(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
