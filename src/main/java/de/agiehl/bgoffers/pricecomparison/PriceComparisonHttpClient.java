package de.agiehl.bgoffers.pricecomparison;

import de.agiehl.bgoffers.config.OfferProperties;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class PriceComparisonHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonHttpClient.class);
    private static final String ACCEPT_LANGUAGE = "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7";

    private final BasicCookieStore cookieStore;
    private final RestClient restClient;
    private final String userAgent;

    PriceComparisonHttpClient(OfferProperties properties, Consumer<ConnectionDetails> connectionObserver) {
        userAgent = properties.http().userAgent();
        cookieStore = new BasicCookieStore();
        restClient = createClient(properties, connectionObserver);
    }

    Response get(
            URI uri,
            String accept,
            URI referer) {
        var headers = requestHeaders(accept, referer);
        LOGGER.debug(
                "Brettspiel-Angebote HTTP-Request: Methode=GET, URI={}, Protokoll={}, Header={}, Cookies={}",
                uri, "HTTP/1.1", headersForLogging(headers), cookiesForLogging(uri));
        var response = restClient
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
        var cookies = cookieStore.getCookies();
        if (cookies.isEmpty()) {
            return "<keine>";
        }
        return cookies.stream()
                .map(cookie -> "%s=%s".formatted(cookie.getName(), cookie.getValue()))
                .collect(Collectors.joining("; "));
    }

    void reset() {
        cookieStore.clear();
    }

    private RestClient createClient(OfferProperties properties, Consumer<ConnectionDetails> connectionObserver) {
        var timeout = Timeout.ofMilliseconds(properties.http().timeout().toMillis());
        var connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new Ipv6FirstDnsResolver())
                .setDefaultConnectionConfig(connectionConfig)
                .build();
        var httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultCookieStore(cookieStore)
                .disableRedirectHandling()
                .addResponseInterceptorLast((response, entity, context) -> {
                    var coreContext = HttpCoreContext.cast(context);
                    var endpoint = coreContext.getEndpointDetails();
                    var tls = coreContext.getSSLSession();
                    var requestId = response.getFirstHeader("CDN-RequestId");
                    connectionObserver.accept(new ConnectionDetails(
                            response.getCode(),
                            response.getVersion().toString(),
                            endpoint == null ? null : endpoint.getLocalAddress(),
                            endpoint == null ? null : endpoint.getRemoteAddress(),
                            tls == null ? null : tls.getProtocol(),
                            tls == null ? null : tls.getCipherSuite(),
                            requestId == null ? null : requestId.getValue()));
                })
                .build();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectionRequestTimeout(properties.http().timeout());
        requestFactory.setReadTimeout(properties.http().timeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
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

    record ConnectionDetails(
            int statusCode,
            String protocol,
            SocketAddress localAddress,
            SocketAddress remoteAddress,
            String tlsProtocol,
            String cipherSuite,
            String requestId) {
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
