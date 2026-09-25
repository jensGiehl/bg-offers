package de.agiehl.bgoffers.scraper;

import com.sun.net.httpserver.HttpServer;
import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.service.ActivityLogService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HttpDocumentClientTest {

    @Test
    void decodesHtmlWithTheCharsetFromTheContentTypeHeader() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/latin1/", exchange -> {
            var response = "<h1>Maestro: Künstler &amp; Rivalen</h1>"
                    .getBytes(StandardCharsets.ISO_8859_1);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=ISO-8859-1");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new HttpDocumentClient(
                    properties(1), mock(ActivityLogService.class));
            var uri = URI.create("http://localhost:%d/latin1/".formatted(server.getAddress().getPort()));

            var document = client.fetch(uri);

            assertThat(document.selectFirst("h1").text()).isEqualTo("Maestro: Künstler & Rivalen");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesTemporaryServerErrorsAndSendsAjaxHeadersForJson() throws Exception {
        var requests = new AtomicInteger();
        var requestedWith = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quicksearch/", exchange -> {
            requestedWith.set(exchange.getRequestHeaders().getFirst("X-Requested-With"));
            var requestNumber = requests.incrementAndGet();
            var response = requestNumber == 1 ? "Temporärer Fehler" : "[{\"name\":\"Scythe\"}]";
            var status = requestNumber == 1 ? 500 : 200;
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var activityLog = mock(ActivityLogService.class);
            var client = new HttpDocumentClient(properties(3), activityLog);
            var uri = URI.create("http://localhost:%d/quicksearch/".formatted(server.getAddress().getPort()));

            var response = client.fetchJson(uri);

            assertThat(response).isEqualTo("[{\"name\":\"Scythe\"}]");
            assertThat(requests).hasValue(2);
            assertThat(requestedWith).hasValue("XMLHttpRequest");
            verify(activityLog).recordHttpRetry(uri.toString(), "HTTP 500", 2, 3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotRetryClientErrors() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/invalid/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
        });
        server.start();
        try {
            var activityLog = mock(ActivityLogService.class);
            var client = new HttpDocumentClient(properties(3), activityLog);
            var uri = URI.create("http://localhost:%d/invalid/".formatted(server.getAddress().getPort()));

            assertThatThrownBy(() -> client.fetch(uri))
                    .isInstanceOf(SourceAccessException.class)
                    .hasMessageContaining("HTTP 422");
            assertThat(requests).hasValue(1);
            verify(activityLog, never()).recordHttpRetry(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsRequestSpecificHeaders() throws Exception {
        var cookie = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/protected/", exchange -> {
            cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            var response = "<h1>Inhalt</h1>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new HttpDocumentClient(properties(1), mock(ActivityLogService.class));
            var uri = URI.create("http://localhost:%d/protected/".formatted(server.getAddress().getPort()));

            client.fetch(uri, Map.of("Cookie", "session=test-session"));

            assertThat(cookie).hasValue("session=test-session");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void logsInWithTheFormAndReusesTheSessionCookies() throws Exception {
        var postedBody = new AtomicReference<String>();
        var loginCookie = new AtomicReference<String>();
        var protectedCookie = new AtomicReference<String>();
        var loginRequests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/login/", exchange -> {
            if (exchange.getRequestMethod().equals("GET")) {
                var cookie = exchange.getRequestHeaders().getFirst("Cookie");
                if (cookie != null && cookie.contains("session=authenticated")) {
                    exchange.sendResponseHeaders(403, -1);
                    exchange.close();
                    return;
                }
                var response = """
                        <form id="login" method="post" action="/login/">
                          <input type="text" name="username">
                          <input type="password" name="password">
                          <input type="hidden" name="t" value="csrf-token">
                        </form>
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Set-Cookie", "session=anonymous; Path=/; HttpOnly");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } else {
                loginRequests.incrementAndGet();
                postedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                loginCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
                exchange.getResponseHeaders().add("Set-Cookie", "session=authenticated; Path=/; HttpOnly");
                exchange.getResponseHeaders().add("Location", "/account/");
                exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        server.createContext("/account/", exchange -> {
            var response = "<h1>Account</h1>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/protected/", exchange -> {
            protectedCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            var response = "<h1>Geschützt</h1>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new HttpDocumentClient(properties(1), mock(ActivityLogService.class));
            var baseUri = URI.create("http://localhost:%d/".formatted(server.getAddress().getPort()));

            client.login(baseUri.resolve("login/"), "name@example.org", "secret with +");
            var document = client.fetch(baseUri.resolve("protected/"));

            assertThat(postedBody).hasValue(
                    "t=csrf-token&username=name%40example.org&password=secret+with+%2B");
            assertThat(loginCookie.get()).contains("session=anonymous");
            assertThat(protectedCookie.get()).contains("session=authenticated");
            assertThat(document.selectFirst("h1").text()).isEqualTo("Geschützt");

            client.login(baseUri.resolve("login/"), "name@example.org", "secret with +");

            assertThat(loginRequests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    private OfferProperties properties(int maxAttempts) {
        var defaults = TestProperties.create();
        return new OfferProperties(
                defaults.sources(),
                new OfferProperties.Http(Duration.ofSeconds(2), "test", maxAttempts, Duration.ZERO, 1),
                defaults.schedule(),
                defaults.telegram(),
                defaults.bgg());
    }
}
