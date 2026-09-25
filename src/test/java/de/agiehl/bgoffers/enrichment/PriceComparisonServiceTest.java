package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.DocumentClient;
import de.agiehl.bgoffers.scraper.SourceAccessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PriceComparisonServiceTest {

    @Test
    void searchesScytheViaHomepageFormAndReadsAvailableAndHistoricalPrices() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var detailUrl = baseUrl + "spiele/scythe/100/";
        var pages = Map.of(
                baseUrl, document("<main>Startseite</main>", baseUrl),
                detailUrl, document("""
                        <div itemprop="offers"><meta itemprop="lowPrice" content="44.90"></div>
                        <span data-absolute-bestprice="32.50"></span>
                        """, detailUrl));
        var client = new RecordingDocumentClient(pages, Map.of(
                baseUrl + "quicksearch/?q=Scythe&source=header", """
                        [
                          {"name":"Anderes Spiel","url":"/spiele/anderes/1/"},
                          {"name":"Scythe","url":"/spiele/scythe/100/"}
                        ]
                        """));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer(), new ObjectMapper());

        var result = service.lookup("Scythe");

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.url()).isEqualTo(detailUrl);
        assertThat(result.availablePrice()).isEqualByComparingTo(new BigDecimal("44.90"));
        assertThat(result.bestPrice()).isEqualByComparingTo(new BigDecimal("32.50"));
        assertThat(client.requestedUris()).containsExactly(
                URI.create(baseUrl),
                URI.create(baseUrl + "quicksearch/?q=Scythe&source=header"),
                URI.create(detailUrl));
    }

    @Test
    void usesBoardGameGeekIdToChooseAmongMultipleScytheResults() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var matchingDetailUrl = baseUrl + "spiele/scythe/1163/";
        var otherDetailUrl = baseUrl + "spiele/scythe/999/";
        var pages = Map.of(
                baseUrl, document("<main>Startseite</main>", baseUrl),
                otherDetailUrl, document("""
                        <a href="https://boardgamegeek.com/boardgame/999999/scythe">BGG</a>
                        <div itemprop="offers"><meta itemprop="lowPrice" content="65.00"></div>
                        """, otherDetailUrl),
                matchingDetailUrl, document("""
                        <a href="https://boardgamegeek.com/boardgame/169786/scythe">BGG</a>
                        <div itemprop="offers"><meta itemprop="lowPrice" content="67.95"></div>
                        <span data-absolute-bestprice="51.61"></span>
                        """, matchingDetailUrl));
        var client = new RecordingDocumentClient(pages, Map.of(
                baseUrl + "quicksearch/?q=Scythe&source=header", """
                        [
                          {"name":"Scythe","url":"/spiele/scythe/999/"},
                          {"name":"Scythe (EN)","url":"/spiele/scythe/1163/"}
                        ]
                        """));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer(), new ObjectMapper());

        var result = service.lookup("Scythe", 169786);

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.url()).isEqualTo(matchingDetailUrl);
        assertThat(result.availablePrice()).isEqualByComparingTo("67.95");
        assertThat(client.requestedUris()).containsExactly(
                URI.create(baseUrl),
                URI.create(baseUrl + "quicksearch/?q=Scythe&source=header"),
                URI.create(otherDetailUrl),
                URI.create(matchingDetailUrl));
    }

    @Test
    void returnsNotFoundInsteadOfAssigningAConsiderablyDifferentEdition() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var client = new RecordingDocumentClient(
                Map.of(baseUrl, document("<main>Startseite</main>", baseUrl)),
                Map.of(
                        baseUrl + "quicksearch/?q=Kingdom+Builder+Anniversary+Edition&source=header", "[]",
                        baseUrl + "quicksearch/?q=Kingdom+Builder+Anniversary&source=header", "[]",
                        baseUrl + "quicksearch/?q=Kingdom+Builder&source=header", """
                                [{"name":"Kingdom Builder: Empire Edition","url":"/spiele/kingdom-builder-empire-edition/5136/"}]
                                """));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer(), new ObjectMapper());

        var result = service.lookup("Kingdom Builder Anniversary Edition (international)");

        assertThat(result.status()).isEqualTo(LookupStatus.NOT_FOUND);
        assertThat(client.requestedUris()).containsExactly(
                URI.create(baseUrl),
                URI.create(baseUrl + "quicksearch/?q=Kingdom+Builder+Anniversary+Edition&source=header"),
                URI.create(baseUrl + "quicksearch/?q=Kingdom+Builder+Anniversary&source=header"),
                URI.create(baseUrl + "quicksearch/?q=Kingdom+Builder&source=header"));
    }

    @Test
    void returnsErrorWhenSearchCannotBeLoaded() {
        var service = new PriceComparisonService(
                uri -> {
                    throw new SourceAccessException("nicht erreichbar");
                }, TestProperties.create(), new GameNameNormalizer(), new ObjectMapper());

        assertThat(service.lookup("Scythe").status()).isEqualTo(LookupStatus.ERROR);
        assertThat(service.healthCheck()).isFalse();
    }

    @Test
    void skipsBundlesWithoutAccessingThePriceComparison() {
        var service = new PriceComparisonService(
                uri -> {
                    throw new AssertionError("Für Bundles darf kein HTTP-Abruf stattfinden");
                }, TestProperties.create(), new GameNameNormalizer(), new ObjectMapper());

        assertThat(service.lookup("Scythe Bundle (deutsch)").status()).isEqualTo(LookupStatus.SKIPPED);
    }

    private static Document document(String html, String baseUri) {
        return Jsoup.parse(html, baseUri);
    }

    private static final class RecordingDocumentClient implements DocumentClient {

        private final Map<String, Document> pages;
        private final Map<String, String> jsonResponses;
        private final List<URI> requestedUris = new ArrayList<>();

        private RecordingDocumentClient(Map<String, Document> pages, Map<String, String> jsonResponses) {
            this.pages = pages;
            this.jsonResponses = jsonResponses;
        }

        @Override
        public Document fetch(URI uri) {
            requestedUris.add(uri);
            var document = pages.get(uri.toString());
            if (document == null) {
                throw new SourceAccessException("Keine Testseite für " + uri);
            }
            return document;
        }

        @Override
        public String fetchJson(URI uri) {
            requestedUris.add(uri);
            var response = jsonResponses.get(uri.toString());
            if (response == null) {
                throw new SourceAccessException("Keine JSON-Testantwort für " + uri);
            }
            return response;
        }

        private List<URI> requestedUris() {
            return List.copyOf(requestedUris);
        }
    }
}
