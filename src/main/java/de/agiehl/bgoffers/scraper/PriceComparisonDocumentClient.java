package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component("priceComparisonDocumentClient")
public class PriceComparisonDocumentClient implements DocumentClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonDocumentClient.class);
    private static final String HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String JSON_ACCEPT = "application/json,text/javascript,*/*;q=0.1";
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9-]+");

    private final OfferProperties properties;
    private Connection session;
    private Document landingPage;
    private Map<String, String> csrfHeaders = Map.of();

    public PriceComparisonDocumentClient(OfferProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized Document fetch(URI uri) {
        requirePriceComparisonOrigin(uri);
        try {
            ensureSessionInitialized();
            if (properties.sources().priceComparison().equals(uri)) {
                return landingPage.clone();
            }
            return fetchDocument(uri, properties.sources().priceComparison());
        } catch (SourceAccessException exception) {
            resetSession();
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
                    csrfHeaders).body();
        } catch (SourceAccessException exception) {
            resetSession();
            throw exception;
        }
    }

    private void ensureSessionInitialized() {
        if (landingPage == null) {
            landingPage = fetchDocument(properties.sources().priceComparison(), null);
        }
    }

    private Document fetchDocument(URI uri, URI referer) {
        var response = executeGet(uri, HTML_ACCEPT, referer, false, Map.of());
        try {
            var document = response.parse();
            rejectChallengePage(uri, document);
            updateCsrfHeaders(document, properties.sources().priceComparison());
            return document;
        } catch (IOException exception) {
            throw new SourceAccessException(
                    "Antwort von %s konnte nicht gelesen werden".formatted(uri), exception);
        }
    }

    private Connection.Response executeGet(
            URI uri,
            String accept,
            URI referer,
            boolean ajaxRequest,
            Map<String, String> headers) {
        IOException lastException = null;
        var attempts = Math.max(1, properties.http().maxAttempts());
        for (var attempt = 1; attempt <= attempts; attempt++) {
            try {
                var request = session().newRequest(uri.toString())
                        .method(Connection.Method.GET)
                        .header("Accept", accept)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(ajaxRequest);
                if (referer != null) {
                    request.referrer(referer.toString());
                }
                if (ajaxRequest) {
                    request.header("X-Requested-With", "XMLHttpRequest");
                }
                headers.forEach(request::header);
                var response = request.execute();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response;
                }
                rejectChallengeResponse(uri, response);
                if (!isRetryable(response.statusCode()) || attempt == attempts) {
                    throw new SourceAccessException("Abruf von %s lieferte HTTP %d"
                            .formatted(uri, response.statusCode()));
                }
                LOGGER.debug("Preisvergleichsabruf von {} lieferte HTTP {}, neuer Versuch {}/{}",
                        uri, response.statusCode(), attempt + 1, attempts);
            } catch (IOException exception) {
                lastException = exception;
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

    private Connection session() {
        if (session == null) {
            session = Jsoup.newSession()
                    .userAgent(properties.http().userAgent())
                    .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
                    .timeout(timeoutMillis())
                    .maxBodySize(0)
                    .followRedirects(true);
        }
        return session;
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

        session().cookieStore().get(baseUri).stream()
                .filter(cookie -> cookie.getName().equalsIgnoreCase("XSRF-TOKEN"))
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

    private int timeoutMillis() {
        return (int) Math.clamp(properties.http().timeout().toMillis(), 1, Integer.MAX_VALUE);
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

    private void rejectChallengeResponse(URI uri, Connection.Response response) {
        var content = response.body().toLowerCase(Locale.ROOT);
        if (content.contains("establishing a secure connection")
                || content.contains("hold tight")
                || content.contains("enable javascript and cookies to continue")) {
            throw new SourceAccessException(
                    "%s hat eine JavaScript-Zugriffsprüfung statt der Inhaltsseite geliefert".formatted(uri));
        }
    }

    private void rejectChallengePage(URI uri, Document document) {
        var content = (document.title() + " " + document.text()).toLowerCase(Locale.ROOT);
        if (content.contains("establishing a secure connection")
                || content.contains("hold tight")
                || content.contains("enable javascript and cookies to continue")) {
            throw new SourceAccessException(
                    "%s hat eine JavaScript-Zugriffsprüfung statt der Inhaltsseite geliefert".formatted(uri));
        }
    }

    private void resetSession() {
        session = null;
        landingPage = null;
        csrfHeaders = Map.of();
    }
}
