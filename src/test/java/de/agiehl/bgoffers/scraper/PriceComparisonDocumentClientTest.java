package de.agiehl.bgoffers.scraper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void loadsLandingPageAndReusesCookiesAndCsrfTokensForSearch() throws Exception {
        var landingRequests = new AtomicInteger();
        var searchCookie = new AtomicReference<String>();
        var searchReferer = new AtomicReference<String>();
        var searchRequestedWith = new AtomicReference<String>();
        var searchCsrfToken = new AtomicReference<String>();
        var searchXsrfToken = new AtomicReference<String>();
        var landingAccept = new AtomicReference<String>();
        var landingFetchDestination = new AtomicReference<String>();
        var landingUpgrade = new AtomicReference<String>();
        var searchFetchDestination = new AtomicReference<String>();
        var searchFetchMode = new AtomicReference<String>();
        var searchFetchSite = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quicksearch/", exchange -> {
            searchCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            searchReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            searchRequestedWith.set(exchange.getRequestHeaders().getFirst("X-Requested-With"));
            searchCsrfToken.set(exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"));
            searchXsrfToken.set(exchange.getRequestHeaders().getFirst("X-XSRF-TOKEN"));
            searchFetchDestination.set(exchange.getRequestHeaders().getFirst("Sec-Fetch-Dest"));
            searchFetchMode.set(exchange.getRequestHeaders().getFirst("Sec-Fetch-Mode"));
            searchFetchSite.set(exchange.getRequestHeaders().getFirst("Sec-Fetch-Site"));
            respond(exchange, 200, "application/json", "[{\"name\":\"Scythe\"}]");
        });
        server.createContext("/", exchange -> {
            landingRequests.incrementAndGet();
            landingAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            landingFetchDestination.set(exchange.getRequestHeaders().getFirst("Sec-Fetch-Dest"));
            landingUpgrade.set(exchange.getRequestHeaders().getFirst("Upgrade-Insecure-Requests"));
            exchange.getResponseHeaders().add("Set-Cookie", "session=landing-session; Path=/; HttpOnly");
            exchange.getResponseHeaders().add("Set-Cookie", "XSRF-TOKEN=xsrf%2Bvalue; Path=/");
            respond(exchange, 200, "text/html; charset=UTF-8", """
                    <html>
                      <head><meta name="csrf-token" content="csrf-value"></head>
                      <body>Startseite</body>
                    </html>
                    """);
        });
        server.start();
        try {
            var baseUri = URI.create("http://localhost:%d/".formatted(server.getAddress().getPort()));
            var client = new PriceComparisonDocumentClient(properties(baseUri));

            var firstResult = client.fetchJson(baseUri.resolve("quicksearch/?q=Scythe&source=header"));
            var secondResult = client.fetchJson(baseUri.resolve("quicksearch/?q=Scythe&source=header"));

            assertThat(firstResult).isEqualTo("[{\"name\":\"Scythe\"}]");
            assertThat(secondResult).isEqualTo(firstResult);
            assertThat(landingRequests).hasValue(1);
            assertThat(searchCookie.get()).contains("session=landing-session", "XSRF-TOKEN=xsrf%2Bvalue");
            assertThat(searchReferer).hasValue(baseUri.toString());
            assertThat(searchRequestedWith).hasValue("XMLHttpRequest");
            assertThat(searchCsrfToken).hasValue("csrf-value");
            assertThat(searchXsrfToken).hasValue("xsrf+value");
            assertThat(landingAccept.get()).contains("image/avif", "application/signed-exchange");
            assertThat(landingFetchDestination).hasValue("document");
            assertThat(landingUpgrade).hasValue("1");
            assertThat(searchFetchDestination).hasValue("empty");
            assertThat(searchFetchMode).hasValue("cors");
            assertThat(searchFetchSite).hasValue("same-origin");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsRequestsToOtherOriginsBeforeOpeningASession() {
        var client = new PriceComparisonDocumentClient(TestProperties.create());

        assertThatThrownBy(() -> client.fetch(URI.create("https://example.org/")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("konfigurierten Ursprung");
    }

    @Test
    void logsThatAForbiddenResponseOccurredWhileLoadingTheLandingPage(CapturedOutput output) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> respond(exchange, 403, "text/html", "Forbidden"));
        server.start();
        try {
            var baseUri = URI.create("http://localhost:%d/".formatted(server.getAddress().getPort()));
            var client = new PriceComparisonDocumentClient(properties(baseUri));

            assertThatThrownBy(() -> client.fetchJson(baseUri.resolve("quicksearch/?q=Scythe&source=header")))
                    .isInstanceOf(SourceAccessException.class)
                    .hasMessageContaining("HTTP 403");
            assertThat(output.getOut())
                    .contains("Typ=Startseite")
                    .contains("HTTP=403")
                    .contains("Ziel-IP=")
                    .contains("IP-Version=")
                    .contains("Protokoll=HTTP/");
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
}
