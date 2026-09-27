package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.enrichment.PriceComparisonService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.scraper.OfferScraper;
import de.agiehl.bgoffers.scraper.ScrapedOffer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartupSystemCheckTest {

    @Test
    void reportsSuccessfulResultsWithinTheDryRunScope() {
        var scraper = scraper(OfferSource.UNKNOWNS);
        var notifier = mock(OfferNotifier.class);
        var priceComparison = priceComparison(true);
        var dryRunContext = new DryRunContext();
        when(scraper.scrape()).thenReturn(List.of(offer(OfferSource.UNKNOWNS)));
        doAnswer(invocation -> {
            assertThat(dryRunContext.active()).isTrue();
            return true;
        }).when(notifier).sendSystemCheck(true, """
                Commit: 0123456
                unknowns.de: 1 Ergebnis
                brettspiel-angebote.de: Preisdaten für „Scythe“ gefunden""");

        new StartupSystemCheck(
                List.of(scraper),
                notifier,
                TestProperties.create(),
                priceComparison,
                dryRunContext,
                new ScraperExecutionCoordinator())
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(true, """
                Commit: 0123456
                unknowns.de: 1 Ergebnis
                brettspiel-angebote.de: Preisdaten für „Scythe“ gefunden""");
        assertThat(dryRunContext.active()).isFalse();
    }

    @Test
    void checksEverySourceAndReportsEmptyAndFailedResults() {
        var successfulScraper = scraper(OfferSource.SPIELE_OFFENSIVE);
        var emptyScraper = scraper(OfferSource.MILAN);
        var failedScraper = scraper(OfferSource.BGG_MARKET);
        var notifier = mock(OfferNotifier.class);
        var priceComparison = priceComparison(true);
        when(successfulScraper.scrape()).thenReturn(List.of(offer(OfferSource.SPIELE_OFFENSIVE)));
        when(emptyScraper.scrape()).thenReturn(List.of());
        when(failedScraper.scrape()).thenThrow(new IllegalStateException("Antwort ungültig"));

        new StartupSystemCheck(
                List.of(failedScraper, emptyScraper, successfulScraper),
                notifier,
                TestProperties.create(),
                priceComparison,
                new DryRunContext(),
                new ScraperExecutionCoordinator())
                .run(mock(ApplicationArguments.class));

        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifier).sendSystemCheck(eq(false), message.capture());
        assertThat(message.getValue()).isEqualTo("""
                Commit: 0123456
                Spiele-Offensive: 1 Ergebnis
                Milan-Spiele: FEHLER – keine Ergebnisse
                BGG Market: FEHLER – Antwort ungültig
                brettspiel-angebote.de: Preisdaten für „Scythe“ gefunden""");
    }

    @Test
    void reportsAFailedPriceComparisonCheck() {
        var scraper = scraper(OfferSource.MILAN);
        var notifier = mock(OfferNotifier.class);
        var priceComparison = priceComparison(false);
        when(scraper.scrape()).thenReturn(List.of(offer(OfferSource.MILAN)));

        new StartupSystemCheck(
                List.of(scraper),
                notifier,
                TestProperties.create(),
                priceComparison,
                new DryRunContext(),
                new ScraperExecutionCoordinator())
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(false, """
                Commit: 0123456
                Milan-Spiele: 1 Ergebnis
                brettspiel-angebote.de: FEHLER – keine Preisdaten für „Scythe“""");
    }

    @Test
    void reportsAnErrorWhenNoSourceIsEnabled() {
        var notifier = mock(OfferNotifier.class);
        var priceComparison = priceComparison(true);

        new StartupSystemCheck(
                List.of(),
                notifier,
                TestProperties.create(),
                priceComparison,
                new DryRunContext(),
                new ScraperExecutionCoordinator())
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(false, """
                Commit: 0123456
                Keine Quellen aktiviert
                brettspiel-angebote.de: Preisdaten für „Scythe“ gefunden""");
    }

    private OfferScraper scraper(OfferSource source) {
        var scraper = mock(OfferScraper.class);
        when(scraper.source()).thenReturn(source);
        return scraper;
    }

    private PriceComparisonService priceComparison(boolean successful) {
        var priceComparison = mock(PriceComparisonService.class);
        when(priceComparison.healthCheck()).thenReturn(successful);
        return priceComparison;
    }

    private ScrapedOffer offer(OfferSource source) {
        return new ScrapedOffer(
                source,
                OfferType.STANDARD,
                "Testspiel",
                "https://example.test/offer",
                null,
                new BigDecimal("19.99"),
                "lieferbar",
                null,
                null);
    }
}
