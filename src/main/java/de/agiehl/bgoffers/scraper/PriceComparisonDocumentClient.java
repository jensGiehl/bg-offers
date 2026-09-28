package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component("priceComparisonDocumentClient")
public class PriceComparisonDocumentClient implements DocumentClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonDocumentClient.class);
    private static final String HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,"
            + "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
    private static final String JSON_ACCEPT = "application/json,text/javascript,*/*;q=0.1";
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9-]+");

    private final OfferProperties properties;
    private final CurlHttpTransport transport;
    private Document landingPage;
    private Map<String, String> csrfHeaders = Map.of();

    public PriceComparisonDocumentClient(OfferProperties properties) {
        this.properties = properties;
        this.transport = new CurlHttpTransport(properties);
    }

    @Override
    public synchronized Document fetch(URI uri) {
        requirePriceComparisonOrigin(uri);
        try {
            ensureSessionInitialized();
            if (properties.sources().priceComparison().equals(uri)) {
                LOGGER.debug("Preisvergleichsabruf verwendet die bereits geladene Startseite: {}", uri);
                return landingPage.clone();
            }
            return fetchDocument(uri, properties.sources().priceComparison(), RequestType.DETAIL_PAGE);
        } catch (SourceAccessException exception) {
            resetSession(exception);
            throw exception;
        }
    }

    @Override
    public synchronized String fetchJson(URI uri) {
        requirePriceComparisonOrigin(uri);
        try {
            ensureSessionInitialized();
            return executeGet(
                    uri,
                    JSON_ACCEPT,
                    properties.sources().priceComparison(),
                    true,
                    csrfHeaders,
                    RequestType.QUICK_SEARCH).bodyAsString();
        } catch (SourceAccessException exception) {
            resetSession(exception);
            throw exception;
        }
    }

    private void ensureSessionInitialized() {
        if (landingPage == null) {
            var baseUri = properties.sources().priceComparison();
            LOGGER.debug("Preisvergleichssitzung wird über die Startseite initialisiert: {}", baseUri);
            landingPage = fetchDocument(baseUri, null, RequestType.LANDING_PAGE);
            LOGGER.debug("Preisvergleichssitzung ist initialisiert: {} CSRF-Header, {} Cookies",
                    csrfHeaders.size(), transport.cookies().size());
        }
    }

    private Document fetchDocument(URI uri, URI referer, RequestType requestType) {
        var response = executeGet(uri, HTML_ACCEPT, referer, false, Map.of(), requestType);
        try {
            var document = Jsoup.parse(new ByteArrayInputStream(response.body()), null, response.uri().toString());
            rejectChallengePage(uri, document);
            updateCsrfHeaders(document, properties.sources().priceComparison());
            return document;
        } catch (IOException exception) {
            throw new SourceAccessException(
                    "Antwort von %s konnte nicht gelesen werden".formatted(uri), exception);
        }
    }

    private CurlHttpTransport.Response executeGet(
            URI uri,
            String accept,
            URI referer,
            boolean ajaxRequest,
            Map<String, String> headers,
            RequestType requestType) {
        IOException lastException = null;
        var attempts = Math.max(1, properties.http().maxAttempts());
        for (var attempt = 1; attempt <= attempts; attempt++) {
            var startedAt = System.nanoTime();
            LOGGER.debug("Preisvergleichsabruf startet: Typ={}, Versuch={}/{}, URI={}",
                    requestType.displayName(), attempt, attempts, uri);
            try {
                var response = transport.get(uri, accept, referer, ajaxRequest, headers);
                var duration = elapsedMillis(startedAt);
                var responseUri = response.uri();
                var contentType = response.contentType() == null ? "unbekannt" : response.contentType();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    LOGGER.debug(
                            "Preisvergleichsabruf beendet: Typ={}, Versuch={}/{}, HTTP={}, URI={}, Antwort-URI={}, Dauer={} ms, Ziel-IP={}, IP-Version={}, Protokoll=HTTP/{}, Content-Type={}, Bytes={}",
                            requestType.displayName(), attempt, attempts, response.statusCode(), uri,
                            responseUri, duration, valueOrUnknown(response.remoteAddress()), response.ipVersion(),
                            valueOrUnknown(response.httpVersion()), contentType, response.body().length);
                    return response;
                }
                LOGGER.warn(
                        "Preisvergleichsabruf fehlgeschlagen: Typ={}, Versuch={}/{}, HTTP={}, URI={}, Antwort-URI={}, Dauer={} ms, Ziel-IP={}, IP-Version={}, Protokoll=HTTP/{}, Server={}, Content-Type={}, Titel={}, Bytes={}",
                        requestType.displayName(), attempt, attempts, response.statusCode(), uri,
                        responseUri, duration, valueOrUnknown(response.remoteAddress()), response.ipVersion(),
                        valueOrUnknown(response.httpVersion()), valueOrUnknown(response.server()), contentType,
                        responseTitle(response), response.body().length);
                rejectChallengeResponse(uri, response);
                if (!isRetryable(response.statusCode()) || attempt == attempts) {
                    throw new SourceAccessException("Abruf von %s lieferte HTTP %d"
                            .formatted(uri, response.statusCode()));
                }
                LOGGER.debug("Preisvergleichsabruf von {} lieferte HTTP {}, neuer Versuch {}/{}",
                        uri, response.statusCode(), attempt + 1, attempts);
            } catch (IOException exception) {
                lastException = exception;
                LOGGER.warn(
                        "Preisvergleichsabruf fehlgeschlagen: Typ={}, Versuch={}/{}, URI={}, Dauer={} ms, Netzwerkfehler={}",
                        requestType.displayName(), attempt, attempts, uri, elapsedMillis(startedAt),
                        exception.getMessage());
                if (attempt == attempts) {
                    break;
                }
                LOGGER.debug("Preisvergleichsabruf von {} ist fehlgeschlagen, neuer Versuch {}/{}",
                        uri, attempt + 1, attempts);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SourceAccessException("Abruf von %s wurde unterbrochen".formatted(uri), exception);
            }
            waitBeforeRetry(uri);
        }
        throw new SourceAccessException("Abruf von %s ist fehlgeschlagen".formatted(uri), lastException);
    }

    private void updateCsrfHeaders(Document document, URI baseUri) {
        var headers = new LinkedHashMap<String, String>();
        var springToken = attribute(document.selectFirst("meta[name=_csrf]"), "content");
        var springHeader = attribute(document.selectFirst("meta[name=_csrf_header]"), "content");
        if (springToken.isPresent() && springHeader.filter(this::validHeaderName).isPresent()) {
            headers.put(springHeader.orElseThrow(), springToken.orElseThrow());
        }

        var commonToken = attribute(document.selectFirst("meta[name=csrf-token]"), "content")
                .or(() -> attribute(document.selectFirst("input[name=_csrf], input[name=_token]"), "value"));
        commonToken.ifPresent(token -> headers.putIfAbsent("X-CSRF-TOKEN", token));

        transport.cookies().entrySet().stream()
                .filter(cookie -> cookie.getKey().equalsIgnoreCase("XSRF-TOKEN"))
                .map(cookie -> decodeCookie(cookie.getValue()))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(token -> headers.put("X-XSRF-TOKEN", token));
        csrfHeaders = Map.copyOf(headers);
    }

    private Optional<String> attribute(Element element, String name) {
        if (element == null || element.attr(name).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(element.attr(name));
    }

    private Optional<String> decodeCookie(String value) {
        try {
            return Optional.of(URLDecoder.decode(
                    value.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean validHeaderName(String name) {
        return HEADER_NAME.matcher(name).matches();
    }

    private void requirePriceComparisonOrigin(URI uri) {
        if (!sameOrigin(properties.sources().priceComparison(), uri)) {
            throw new IllegalArgumentException(
                    "Preisvergleichs-Client darf nur den konfigurierten Ursprung aufrufen");
        }
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void waitBeforeRetry(URI uri) {
        try {
            Thread.sleep(properties.http().retryDelay());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SourceAccessException(
                    "Warten auf erneuten Abruf von %s wurde unterbrochen".formatted(uri), exception);
        }
    }

    private void rejectChallengeResponse(URI uri, CurlHttpTransport.Response response) {
        var content = response.bodyAsString().toLowerCase(Locale.ROOT);
        if (content.contains("establishing a secure connection")
                || content.contains("hold tight")
                || content.contains("enable javascript and cookies to continue")) {
            LOGGER.warn("JavaScript-Zugriffsprüfung in der HTTP-Antwort erkannt: {}", uri);
            throw new SourceAccessException(
                    "%s hat eine JavaScript-Zugriffsprüfung statt der Inhaltsseite geliefert".formatted(uri));
        }
    }

    private void rejectChallengePage(URI uri, Document document) {
        var content = (document.title() + " " + document.text()).toLowerCase(Locale.ROOT);
        if (content.contains("establishing a secure connection")
                || content.contains("hold tight")
                || content.contains("enable javascript and cookies to continue")) {
            LOGGER.warn("JavaScript-Zugriffsprüfung in der HTML-Seite erkannt: {}", uri);
            throw new SourceAccessException(
                    "%s hat eine JavaScript-Zugriffsprüfung statt der Inhaltsseite geliefert".formatted(uri));
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String responseTitle(CurlHttpTransport.Response response) {
        var title = Jsoup.parse(response.bodyAsString()).title().replaceAll("\\s+", " ").trim();
        if (title.isBlank()) {
            return "unbekannt";
        }
        return title.substring(0, Math.min(120, title.length()));
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unbekannt" : value;
    }

    private void resetSession(SourceAccessException exception) {
        LOGGER.debug("Preisvergleichssitzung wird nach einem Fehler verworfen: {}", exception.getMessage());
        transport.reset();
        landingPage = null;
        csrfHeaders = Map.of();
    }

    @PreDestroy
    void close() {
        transport.close();
    }

    private enum RequestType {
        LANDING_PAGE("Startseite"),
        QUICK_SEARCH("Schnellsuche"),
        DETAIL_PAGE("Detailseite");

        private final String displayName;

        RequestType(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }
}
