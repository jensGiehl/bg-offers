package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.service.ActivityLogService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HttpDocumentClient implements SessionDocumentClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpDocumentClient.class);
    private static final String HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String JSON_ACCEPT = "application/json,text/javascript,*/*;q=0.1";

    private final OfferProperties properties;
    private final ActivityLogService activityLogService;
    private final CookieManager cookieManager;
    private final HttpClient httpClient;

    public HttpDocumentClient(OfferProperties properties, ActivityLogService activityLogService) {
        this.properties = properties;
        this.activityLogService = activityLogService;
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.http().timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(cookieManager)
                .build();
    }

    @Override
    public Document fetch(URI uri) {
        return fetch(uri, Map.of());
    }

    @Override
    public Document fetch(URI uri, Map<String, String> headers) {
        var response = sendGet(uri, HTML_ACCEPT, false, headers);
        return parseDocument(uri, response);
    }

    @Override
    public void login(URI loginUri, String username, String password) {
        clearSession(loginUri);
        var loginPage = fetch(loginUri);
        var loginForm = loginPage.selectFirst("form#login");
        if (loginForm == null) {
            return;
        }

        var formFields = new LinkedHashMap<String, String>();
        loginForm.select("input[type=hidden][name]").forEach(input ->
                formFields.put(input.attr("name"), input.attr("value")));
        formFields.put("username", username);
        formFields.put("password", password);

        var action = loginForm.absUrl("action");
        var actionUri = action.isBlank() ? loginUri : URI.create(action);
        requireSameOrigin(loginUri, actionUri);
        var response = sendForm(actionUri, loginUri, formFields);
        var responseDocument = parseDocument(loginUri, response);
        if (responseDocument.selectFirst("form#login input[name=password]") != null) {
            throw new SourceAccessException(
                    "Anmeldung bei %s ist fehlgeschlagen; Benutzername und Passwort prüfen"
                            .formatted(loginUri.getHost()));
        }
    }

    private void clearSession(URI uri) {
        var cookieStore = cookieManager.getCookieStore();
        for (var cookie : cookieStore.get(uri)) {
            cookieStore.remove(uri, cookie);
        }
    }

    private Document parseDocument(URI requestedUri, HttpResponse<byte[]> response) {
        try {
            var document = Jsoup.parse(
                    new ByteArrayInputStream(response.body()),
                    responseCharset(response),
                    response.uri().toString());
            rejectChallengePage(requestedUri, document);
            return document;
        } catch (IOException exception) {
            throw new SourceAccessException(
                    "Antwort von %s konnte nicht gelesen werden".formatted(requestedUri), exception);
        }
    }

    @Override
    public String fetchJson(URI uri) {
        return new String(sendGet(uri, JSON_ACCEPT, true, Map.of()).body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> sendGet(
            URI uri,
            String accept,
            boolean ajaxRequest,
            Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(properties.http().timeout())
                .header("User-Agent", properties.http().userAgent())
                .header("Accept", accept)
                .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.7");
        if (ajaxRequest) {
            builder.header("X-Requested-With", "XMLHttpRequest");
        }
        headers.forEach(builder::header);
        return send(builder.GET().build());
    }

    private HttpResponse<byte[]> sendForm(
            URI uri,
            URI referer,
            Map<String, String> fields) {
        var request = HttpRequest.newBuilder(uri)
                .timeout(properties.http().timeout())
                .header("User-Agent", properties.http().userAgent())
                .header("Accept", HTML_ACCEPT)
                .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header("Referer", referer.toString())
                .header("Origin", "%s://%s".formatted(uri.getScheme(), uri.getAuthority()))
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(fields)))
                .build();
        return send(request);
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        var uri = request.uri();
        IOException lastException = null;
        var attempts = Math.max(1, properties.http().maxAttempts());
        for (var attempt = 1; attempt <= attempts; attempt++) {
            try {
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response;
                }
                if (!isRetryable(response.statusCode()) || attempt == attempts) {
                    throw new SourceAccessException("Abruf von %s lieferte HTTP %d"
                            .formatted(uri, response.statusCode()));
                }
                LOGGER.debug("Abruf von {} lieferte HTTP {}, neuer Versuch {}/{}",
                        uri, response.statusCode(), attempt + 1, attempts);
                recordRetry(uri, "HTTP " + response.statusCode(), attempt + 1, attempts);
            } catch (IOException exception) {
                lastException = exception;
                if (attempt == attempts) {
                    break;
                }
                LOGGER.debug("Abruf von {} ist fehlgeschlagen, neuer Versuch {}/{}",
                        uri, attempt + 1, attempts);
                recordRetry(uri, "Netzwerkfehler", attempt + 1, attempts);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SourceAccessException("Abruf von %s wurde unterbrochen".formatted(uri), exception);
            }
            waitBeforeRetry(uri);
        }
        throw new SourceAccessException("Abruf von %s ist fehlgeschlagen".formatted(uri), lastException);
    }

    private String encodeForm(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> "%s=%s".formatted(
                        URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8),
                        URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)))
                .collect(Collectors.joining("&"));
    }

    private void requireSameOrigin(URI loginUri, URI actionUri) {
        if (!loginUri.getScheme().equalsIgnoreCase(actionUri.getScheme())
                || !loginUri.getHost().equalsIgnoreCase(actionUri.getHost())
                || effectivePort(loginUri) != effectivePort(actionUri)) {
            throw new SourceAccessException(
                    "Login-Formular von %s verweist auf eine fremde Adresse".formatted(loginUri.getHost()));
        }
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
            throw new SourceAccessException("Warten auf erneuten Abruf von %s wurde unterbrochen".formatted(uri), exception);
        }
    }

    private void recordRetry(URI uri, String reason, int nextAttempt, int maximumAttempts) {
        var target = "%s://%s%s".formatted(uri.getScheme(), uri.getAuthority(), uri.getPath());
        try {
            activityLogService.recordHttpRetry(target, reason, nextAttempt, maximumAttempts);
        } catch (RuntimeException exception) {
            LOGGER.warn("HTTP-Retry für {} konnte nicht im Activity Log gespeichert werden: {}",
                    uri, exception.getMessage());
        }
    }

    private String responseCharset(HttpResponse<?> response) {
        try {
            return response.headers().firstValue("Content-Type")
                    .map(MediaType::parseMediaType)
                    .map(MediaType::getCharset)
                    .map(java.nio.charset.Charset::name)
                    .orElse(null);
        } catch (InvalidMediaTypeException exception) {
            LOGGER.debug("Ungültiger Content-Type von {}: {}", response.uri(), exception.getMessage());
            return null;
        }
    }

    private void rejectChallengePage(URI uri, Document document) {
        var title = document.title().toLowerCase();
        var text = document.body() == null ? "" : document.body().text().toLowerCase();
        if (title.contains("just a moment") || text.contains("enable javascript and cookies to continue")) {
            throw new SourceAccessException("%s hat eine Browser-Prüfung statt der Inhaltsseite geliefert".formatted(uri));
        }
    }
}
