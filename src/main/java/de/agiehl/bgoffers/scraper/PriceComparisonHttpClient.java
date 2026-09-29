package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class PriceComparisonHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonHttpClient.class);
    private static final String ACCEPT_LANGUAGE = "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7";

    private final CookieManager cookieManager;
    private final RestClient negotiatedClient;
    private final RestClient http1Client;
    private final String userAgent;

    PriceComparisonHttpClient(OfferProperties properties) {
        userAgent = properties.http().userAgent();
        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        negotiatedClient = createClient(properties, null);
        http1Client = createClient(properties, HttpClient.Version.HTTP_1_1);
    }

    Response get(
            URI uri,
            String accept,
            URI referer,
            HttpProtocol protocol) {
        var headers = requestHeaders(accept, referer);
        LOGGER.debug(
                "Brettspiel-Angebote HTTP-Request: Methode=GET, URI={}, Protokoll={}, Header={}, Cookies={}",
                uri, protocol.displayName(), headersForLogging(headers), cookiesForLogging(uri));
        var response = client(protocol)
                .get()
                .uri(uri)
                .headers(requestHeaders -> requestHeaders.putAll(headers))
                .exchange((request, clientResponse) -> new Response(
                        clientResponse.getStatusCode().value(),
                        request.getURI(),
                        clientResponse.getHeaders().getLocation(),
                        clientResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        clientResponse.getHeaders().getFirst(HttpHeaders.SERVER),
                        headersForLogging(clientResponse.getHeaders()),
                        clientResponse.getBody().readAllBytes()));
        LOGGER.debug(
                "Brettspiel-Angebote HTTP-Response: URI={}, HTTP={}, Header={}, Bytes={}, Cookies={}",
                response.uri(), response.statusCode(), response.headers(),
                response.body().length, cookiesForLogging(uri));
        return response;
    }

    String cookiesForLogging(URI uri) {
        var cookies = cookieManager.getCookieStore().get(uri);
        if (cookies.isEmpty()) {
            return "<keine>";
        }
        return cookies.stream()
                .map(cookie -> "%s=%s".formatted(cookie.getName(), cookie.getValue()))
                .collect(Collectors.joining("; "));
    }

    void reset() {
        cookieManager.getCookieStore().removeAll();
    }

    private RestClient createClient(OfferProperties properties, HttpClient.Version version) {
        var httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(properties.http().timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cookieManager);
        if (version != null) {
            httpClientBuilder.version(version);
        }
        var requestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
        requestFactory.setReadTimeout(properties.http().timeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private RestClient client(HttpProtocol protocol) {
        return protocol == HttpProtocol.HTTP_1_1 ? http1Client : negotiatedClient;
    }

    private HttpHeaders requestHeaders(String accept, URI referer) {
        var requestHeaders = new HttpHeaders();
        requestHeaders.set(HttpHeaders.USER_AGENT, userAgent);
        requestHeaders.set(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE);
        requestHeaders.set(HttpHeaders.ACCEPT, accept);
        requestHeaders.set("Sec-Fetch-Dest", "document");
        requestHeaders.set("Sec-Fetch-Mode", "navigate");
        requestHeaders.set("Sec-Fetch-Site", referer == null ? "none" : "same-origin");
        requestHeaders.set("Sec-Fetch-User", "?1");
        requestHeaders.set("Upgrade-Insecure-Requests", "1");
        requestHeaders.set("Priority", "u=0, i");
        if (referer != null) {
            requestHeaders.set(HttpHeaders.REFERER, referer.toString());
        }
        return requestHeaders;
    }

    private Map<String, List<String>> headersForLogging(HttpHeaders headers) {
        var sortedHeaders = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) -> sortedHeaders.put(name, List.copyOf(values)));
        return new LinkedHashMap<>(sortedHeaders);
    }

    enum HttpProtocol {
        NEGOTIATED("ausgehandelt"),
        HTTP_1_1("HTTP/1.1");

        private final String displayName;

        HttpProtocol(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }

    record Response(
            int statusCode,
            URI uri,
            URI location,
            String contentType,
            String server,
            Map<String, List<String>> headers,
            byte[] body) {

        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
