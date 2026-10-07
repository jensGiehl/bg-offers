package de.agiehl.bgoffers.pricecomparison;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.SourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public final class PriceComparisonHttpClient implements PriceComparisonClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonHttpClient.class);

    private final OfferProperties properties;
    private final RestClient restClient;

    public PriceComparisonHttpClient(
            OfferProperties properties,
            @Value("${offers.http.price-comparison-timeout:60s}") Duration timeout) {
        this.properties = properties;
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.http().timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PriceComparisonResult lookup(String name, Integer bggId) {
        var baseUrl = properties.sources().priceComparison().toString().replaceAll("/+$", "");
        var query = "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        if (bggId != null) {
            query += "&bggId=" + bggId;
        }
        var uri = URI.create(baseUrl + "/api/v1/prices?" + query);
        var attempts = Math.max(1, properties.http().maxAttempts());
        RestClientException lastException = null;
        for (var attempt = 1; attempt <= attempts; attempt++) {
            LOGGER.debug("Preisservice-Abruf: Versuch={}/{}, URI={}", attempt, attempts, uri);
            try {
                var response = restClient.get()
                        .uri(uri)
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange((request, clientResponse) -> new Response(
                                clientResponse.getStatusCode().value(),
                                clientResponse.getStatusCode().is2xxSuccessful()
                                        ? clientResponse.bodyTo(PriceResponse.class) : null));
                LOGGER.debug("Preisservice-Antwort: URI={}, HTTP={}", uri, response.statusCode());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return priceResult(response.body(), bggId);
                }
                if (!isRetryable(response.statusCode()) || attempt == attempts) {
                    throw new SourceAccessException("Preisservice unter %s lieferte HTTP %d"
                            .formatted(uri, response.statusCode()));
                }
                LOGGER.warn("Preisservice unter {} lieferte HTTP {}, neuer Versuch {}/{}",
                        uri, response.statusCode(), attempt + 1, attempts);
            } catch (RestClientException exception) {
                lastException = exception;
                LOGGER.warn("Preisservice-Abruf von {} ist fehlgeschlagen: {}", uri, exception.getMessage());
                if (attempt == attempts) {
                    break;
                }
            }
            waitBeforeRetry(uri);
        }
        throw new SourceAccessException("Preisservice-Abruf von %s ist fehlgeschlagen".formatted(uri), lastException);
    }

    private PriceComparisonResult priceResult(PriceResponse response, Integer bggId) {
        if (response == null || response.status() == null) {
            throw new SourceAccessException("Preisservice lieferte keine gültige Antwort");
        }
        return switch (response.status()) {
            case FOUND -> {
                if (!"EUR".equals(response.currency())
                        || isNegative(response.availablePrice()) || isNegative(response.bestPrice())
                        || response.availablePrice() == null && response.bestPrice() == null
                        || bggId != null && !Long.valueOf(bggId).equals(response.matchedBggId())) {
                    throw new SourceAccessException("Preisservice lieferte ungültige Preise oder eine abweichende BGG-ID");
                }
                yield new PriceComparisonResult(LookupStatus.FOUND, response.url(),
                        response.availablePrice(), response.bestPrice());
            }
            case NOT_FOUND -> PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND);
            case SKIPPED -> PriceComparisonResult.withStatus(LookupStatus.SKIPPED);
            case ERROR -> {
                LOGGER.warn("Preisservice meldet einen Fehler: {}", response.errorCode());
                yield PriceComparisonResult.withStatus(LookupStatus.ERROR);
            }
        };
    }

    private boolean isNegative(BigDecimal price) {
        return price != null && price.signum() < 0;
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
                    "Warten auf erneuten Preisservice-Abruf von %s wurde unterbrochen".formatted(uri), exception);
        }
    }

    private record Response(int statusCode, PriceResponse body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PriceResponse(
            Status status,
            Long matchedBggId,
            String url,
            String currency,
            BigDecimal availablePrice,
            BigDecimal bestPrice,
            String errorCode) {
    }

    private enum Status {
        FOUND, NOT_FOUND, ERROR, SKIPPED
    }
}
