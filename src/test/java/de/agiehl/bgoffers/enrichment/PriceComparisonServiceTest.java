package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.PriceComparisonDocumentClient;
import de.agiehl.bgoffers.scraper.SourceAccessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PriceComparisonServiceTest {

    @Test
    void searchesScytheViaRedirectAndReadsAvailableAndHistoricalPrices() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Scythe";
        var detailUrl = baseUrl + "spiele/scythe/100/";
        var client = mock(PriceComparisonDocumentClient.class);
        when(client.search(URI.create(searchUrl))).thenReturn(document("""
                <div itemprop="offers"><meta itemprop="lowPrice" content="44.90"></div>
                <span data-absolute-bestprice="32.50"></span>
                """, detailUrl));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("Scythe");

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.url()).isEqualTo(detailUrl);
        assertThat(result.availablePrice()).isEqualByComparingTo(new BigDecimal("44.90"));
        assertThat(result.bestPrice()).isEqualByComparingTo(new BigDecimal("32.50"));
        verify(client).search(URI.create(searchUrl));
    }

    @Test
    void usesTheNormalizedAndEncodedGameNameForSearch() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Kingdom+Builder";
        var detailUrl = baseUrl + "spiele/kingdom-builder/200/";
        var client = mock(PriceComparisonDocumentClient.class);
        when(client.search(URI.create(searchUrl))).thenReturn(document("""
                <div itemprop="offers"><meta itemprop="lowPrice" content="29.99"></div>
                """, detailUrl));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("  Kingdom Builder (deutsch)  ");

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        verify(client).search(URI.create(searchUrl));
    }

    @Test
    void rejectsRedirectTargetWithDifferentBoardGameGeekId() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Scythe";
        var detailUrl = baseUrl + "spiele/scythe/999/";
        var client = mock(PriceComparisonDocumentClient.class);
        when(client.search(URI.create(searchUrl))).thenReturn(document("""
                <a href="https://boardgamegeek.com/boardgame/999999/scythe">BGG</a>
                <div itemprop="offers"><meta itemprop="lowPrice" content="65.00"></div>
                """, detailUrl));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("Scythe", 169786);

        assertThat(result.status()).isEqualTo(LookupStatus.NOT_FOUND);
    }

    @Test
    void returnsNotFoundWhenRedirectTargetHasNoAvailablePrice() {
        var baseUrl = "https://www.brettspiel-angebote.de/";
        var searchUrl = baseUrl + "suche/?s=Unbekanntes+Spiel";
        var client = mock(PriceComparisonDocumentClient.class);
        when(client.search(URI.create(searchUrl))).thenReturn(
                document("<html><body>Kein Preis</body></html>", searchUrl));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        var result = service.lookup("Unbekanntes Spiel");

        assertThat(result.status()).isEqualTo(LookupStatus.NOT_FOUND);
    }

    @Test
    void returnsErrorWhenSearchCannotBeLoaded() {
        var client = mock(PriceComparisonDocumentClient.class);
        when(client.search(URI.create("https://www.brettspiel-angebote.de/suche/?s=Scythe")))
                .thenThrow(new SourceAccessException("nicht erreichbar"));
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        assertThat(service.lookup("Scythe").status()).isEqualTo(LookupStatus.ERROR);
        assertThat(service.healthCheck()).isFalse();
    }

    @Test
    void skipsBundlesWithoutAccessingThePriceComparison() {
        var client = mock(PriceComparisonDocumentClient.class);
        var service = new PriceComparisonService(
                client, TestProperties.create(), new GameNameNormalizer());

        assertThat(service.lookup("Scythe Bundle (deutsch)").status()).isEqualTo(LookupStatus.SKIPPED);
        verifyNoInteractions(client);
    }

    private static Document document(String html, String baseUri) {
        return Jsoup.parse(html, baseUri);
    }

}
