package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Component("priceComparisonDocumentClient")
public class PriceComparisonDocumentClient implements DocumentClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonDocumentClient.class);
    private static final String HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,"
            + "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";

    private final OfferProperties properties;
    private final PriceComparisonHttpClient httpClient;
    private Document landingPage;

    public PriceComparisonDocumentClient(OfferProperties properties) {
        this.properties = properties;
        this.httpClient = new PriceComparisonHttpClient(properties);
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
    public synchronized Document fetchFollowingRedirect(URI uri) {
        requirePriceComparisonOrigin(uri);
        try {
            ensureSessionInitialized();
            var redirect = executeGet(
                    uri, HTML_ACCEPT, properties.sources().priceComparison(), RequestType.SEARCH);
            if (redirect.location() == null) {
                throw new SourceAccessException(
                        "Suche unter %s lieferte keinen Location-Header".formatted(uri));
            }
            var targetUri = uri.resolve(redirect.location());
            requirePriceComparisonOrigin(targetUri);
            LOGGER.debug("Preisvergleichssuche leitet auf Detailseite weiter: {} -> {}", uri, targetUri);
            return fetchDocument(targetUri, uri, RequestType.DETAIL_PAGE);
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
            LOGGER.debug("Preisvergleichssitzung ist initialisiert: {} Cookies",
                    httpClient.cookies(baseUri).size());
        }
    }

    private Document fetchDocument(URI uri, URI referer, RequestType requestType) {
        var response = executeGet(uri, HTML_ACCEPT, referer, requestType);
        try {
            var document = Jsoup.parse(new ByteArrayInputStream(response.body()), null, response.uri().toString());
            rejectChallengePage(uri, document);
            return document;
        } catch (IOException exception) {
            throw new SourceAccessException(
                    "Antwort von %s konnte nicht gelesen werden".formatted(uri), exception);
        }
    }

    private PriceComparisonHttpClient.Response executeGet(
            URI uri,
            String accept,
            URI referer,
            RequestType requestType) {
        RestClientException lastException = null;
        var attempts = Math.max(1, properties.http().maxAttempts());
        for (var attempt = 1; attempt <= attempts; attempt++) {
            var startedAt = System.nanoTime();
            LOGGER.debug("Preisvergleichsabruf startet: Typ={}, Versuch={}/{}, URI={}",
                    requestType.displayName(), attempt, attempts, uri);
            try {
                var response = httpClient.get(uri, accept, referer, requestType.httpProtocol());
                var duration = elapsedMillis(startedAt);
                var contentType = response.contentType() == null ? "unbekannt" : response.contentType();
                if (requestType.accepts(response.statusCode())) {
                    LOGGER.debug(
                            "Preisvergleichsabruf beendet: Typ={}, Versuch={}/{}, HTTP={}, URI={}, Location={}, Dauer={} ms, Content-Type={}, Bytes={}",
                            requestType.displayName(), attempt, attempts, response.statusCode(), uri,
                            valueOrUnknown(response.location()), duration, contentType, response.body().length);
                    return response;
                }
                LOGGER.warn(
                        "Preisvergleichsabruf fehlgeschlagen: Typ={}, Versuch={}/{}, HTTP={}, URI={}, Dauer={} ms, Server={}, Content-Type={}, Titel={}, Bytes={}",
                        requestType.displayName(), attempt, attempts, response.statusCode(), uri,
                        duration, valueOrUnknown(response.server()), contentType,
                        responseTitle(response), response.body().length);
                rejectChallengeResponse(uri, response);
                if (!isRetryable(response.statusCode()) || attempt == attempts) {
                    throw new SourceAccessException("Abruf von %s lieferte HTTP %d"
                            .formatted(uri, response.statusCode()));
                }
                LOGGER.debug("Preisvergleichsabruf von {} lieferte HTTP {}, neuer Versuch {}/{}",
                        uri, response.statusCode(), attempt + 1, attempts);
            } catch (RestClientException exception) {
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
            }
            waitBeforeRetry(uri);
        }
        throw new SourceAccessException("Abruf von %s ist fehlgeschlagen".formatted(uri), lastException);
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

    private void rejectChallengeResponse(URI uri, PriceComparisonHttpClient.Response response) {
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

    private String responseTitle(PriceComparisonHttpClient.Response response) {
        var title = Jsoup.parse(response.bodyAsString()).title().replaceAll("\\s+", " ").trim();
        if (title.isBlank()) {
            return "unbekannt";
        }
        return title.substring(0, Math.min(120, title.length()));
    }

    private String valueOrUnknown(Object value) {
        return value == null || value.toString().isBlank() ? "unbekannt" : value.toString();
    }

    private void resetSession(SourceAccessException exception) {
        LOGGER.debug("Preisvergleichssitzung wird nach einem Fehler verworfen: {}", exception.getMessage());
        httpClient.reset();
        landingPage = null;
    }

    private enum RequestType {
        LANDING_PAGE("Startseite", PriceComparisonHttpClient.HttpProtocol.NEGOTIATED, false),
        SEARCH("Suche", PriceComparisonHttpClient.HttpProtocol.NEGOTIATED, true),
        DETAIL_PAGE("Detailseite", PriceComparisonHttpClient.HttpProtocol.HTTP_1_1, false);

        private final String displayName;
        private final PriceComparisonHttpClient.HttpProtocol httpProtocol;
        private final boolean redirectExpected;

        RequestType(
                String displayName,
                PriceComparisonHttpClient.HttpProtocol httpProtocol,
                boolean redirectExpected) {
            this.displayName = displayName;
            this.httpProtocol = httpProtocol;
            this.redirectExpected = redirectExpected;
        }

        private String displayName() {
            return displayName;
        }

        private PriceComparisonHttpClient.HttpProtocol httpProtocol() {
            return httpProtocol;
        }

        private boolean accepts(int statusCode) {
            if (redirectExpected) {
                return statusCode >= 300 && statusCode < 400;
            }
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
