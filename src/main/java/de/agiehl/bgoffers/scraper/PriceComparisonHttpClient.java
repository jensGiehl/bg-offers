package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

final class PriceComparisonHttpClient {

    private final CookieManager cookieManager;
    private final RestClient negotiatedClient;
    private final RestClient http1Client;

    PriceComparisonHttpClient(OfferProperties properties) {
        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        negotiatedClient = createClient(properties, null);
        http1Client = createClient(properties, HttpClient.Version.HTTP_1_1);
    }

    Response get(
            URI uri,
            String accept,
            URI referer,
            boolean ajaxRequest,
            Map<String, String> headers,
            HttpProtocol protocol) {
        return client(protocol)
                .get()
                .uri(uri)
                .headers(requestHeaders -> configureHeaders(
                        requestHeaders, accept, referer, ajaxRequest, headers))
                .exchange((request, response) -> new Response(
                        response.getStatusCode().value(),
                        request.getURI(),
                        response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        response.getHeaders().getFirst(HttpHeaders.SERVER),
                        response.getBody().readAllBytes()));
    }

    Map<String, String> cookies(URI uri) {
        return cookieManager.getCookieStore().get(uri).stream()
                .collect(Collectors.toUnmodifiableMap(
                        HttpCookie::getName,
                        HttpCookie::getValue,
                        (first, second) -> second));
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
                .defaultHeader(HttpHeaders.USER_AGENT, properties.http().userAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                .build();
    }

    private RestClient client(HttpProtocol protocol) {
        return protocol == HttpProtocol.HTTP_1_1 ? http1Client : negotiatedClient;
    }

    private void configureHeaders(
            HttpHeaders requestHeaders,
            String accept,
            URI referer,
            boolean ajaxRequest,
            Map<String, String> headers) {
        requestHeaders.set(HttpHeaders.ACCEPT, accept);
        if (ajaxRequest) {
            requestHeaders.set("X-Requested-With", "XMLHttpRequest");
            requestHeaders.set("Sec-Fetch-Dest", "empty");
            requestHeaders.set("Sec-Fetch-Mode", "cors");
            requestHeaders.set("Sec-Fetch-Site", "same-origin");
            requestHeaders.set("Priority", "u=1, i");
        } else {
            requestHeaders.set("Sec-Fetch-Dest", "document");
            requestHeaders.set("Sec-Fetch-Mode", "navigate");
            requestHeaders.set("Sec-Fetch-Site", referer == null ? "none" : "same-origin");
            requestHeaders.set("Sec-Fetch-User", "?1");
            requestHeaders.set("Upgrade-Insecure-Requests", "1");
            requestHeaders.set("Priority", "u=0, i");
        }
        if (referer != null) {
            requestHeaders.set(HttpHeaders.REFERER, referer.toString());
        }
        headers.forEach(requestHeaders::set);
    }

    enum HttpProtocol {
        NEGOTIATED,
        HTTP_1_1
    }

    record Response(
            int statusCode,
            URI uri,
            String contentType,
            String server,
            byte[] body) {

        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
