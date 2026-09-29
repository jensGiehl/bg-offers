package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.DocumentClient;
import de.agiehl.bgoffers.scraper.SourceAccessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PriceComparisonServiceTest {

    @Test
    void searchesScytheViaRedirectAndReadsAvailableAndHistoricalPrices() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Scythe";
        var detailUrl = baseUrl + "spiele/scythe/100/";
        var client = new RecordingDocumentClient(Map.of(
                searchUrl, document("""
                        <div itemprop="offers"><meta itemprop="lowPrice" content="44.90"></div>
                        <span data-absolute-bestprice="32.50"></span>
                        """, detailUrl)));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("Scythe");

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.url()).isEqualTo(detailUrl);
        assertThat(result.availablePrice()).isEqualByComparingTo(new BigDecimal("44.90"));
        assertThat(result.bestPrice()).isEqualByComparingTo(new BigDecimal("32.50"));
        assertThat(client.requestedUris()).containsExactly(URI.create(searchUrl));
    }

    @Test
    void usesTheNormalizedAndEncodedGameNameForSearch() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Kingdom+Builder";
        var detailUrl = baseUrl + "spiele/kingdom-builder/200/";
        var client = new RecordingDocumentClient(Map.of(
                searchUrl, document("""
                        <div itemprop="offers"><meta itemprop="lowPrice" content="29.99"></div>
                        """, detailUrl)));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("  Kingdom Builder (deutsch)  ");

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(client.requestedUris()).containsExactly(URI.create(searchUrl));
    }

    @Test
    void rejectsRedirectTargetWithDifferentBoardGameGeekId() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Scythe";
        var detailUrl = baseUrl + "spiele/scythe/999/";
        var client = new RecordingDocumentClient(Map.of(
                searchUrl, document("""
                        <a href="https://boardgamegeek.com/boardgame/999999/scythe">BGG</a>
                        <div itemprop="offers"><meta itemprop="lowPrice" content="65.00"></div>
                        """, detailUrl)));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("Scythe", 169786);

        assertThat(result.status()).isEqualTo(LookupStatus.NOT_FOUND);
    }

    @Test
    void returnsNotFoundWhenRedirectTargetHasNoAvailablePrice() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Unbekanntes+Spiel";
        var client = new RecordingDocumentClient(Map.of(
                searchUrl, document("<html><body>Kein Preis</body></html>", searchUrl)));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("Unbekanntes Spiel");

        assertThat(result.status()).isEqualTo(LookupStatus.NOT_FOUND);
    }

    @Test
    void returnsErrorWhenSearchCannotBeLoaded() {
        var service = new PriceComparisonService(
                uri -> {
                    throw new SourceAccessException("nicht erreichbar");
                }, TestProperties.create(), new GameNameNormalizer());

        assertThat(service.lookup("Scythe").status()).isEqualTo(LookupStatus.ERROR);
        assertThat(service.healthCheck()).isFalse();
    }

    @Test
    void skipsBundlesWithoutAccessingThePriceComparison() {
        var service = new PriceComparisonService(
                uri -> {
                    throw new AssertionError("Für Bundles darf kein HTTP-Abruf stattfinden");
                }, TestProperties.create(), new GameNameNormalizer());

        assertThat(service.lookup("Scythe Bundle (deutsch)").status()).isEqualTo(LookupStatus.SKIPPED);
    }

    private static Document document(String html, String baseUri) {
        return Jsoup.parse(html, baseUri);
    }

    private static final class RecordingDocumentClient implements DocumentClient {

        private final Map<String, Document> redirectedPages;
        private final List<URI> requestedUris = new ArrayList<>();

        private RecordingDocumentClient(Map<String, Document> redirectedPages) {
            this.redirectedPages = redirectedPages;
        }

        @Override
        public Document fetch(URI uri) {
            throw new AssertionError("Direkter Seitenabruf war nicht erwartet: " + uri);
        }

        @Override
        public Document fetchFollowingRedirect(URI uri) {
            requestedUris.add(uri);
            var document = redirectedPages.get(uri.toString());
            if (document == null) {
                throw new SourceAccessException("Keine Testseite für " + uri);
            }
            return document;
        }

        private List<URI> requestedUris() {
            return List.copyOf(requestedUris);
        }
    }
}
