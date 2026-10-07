package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonService;
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
    void reportsAndRecordsStartupOutsideTheDryRunScope() {
        var scraper = scraper(OfferSource.UNKNOWNS);
        var notifier = mock(OfferNotifier.class);
        var priceComparison = priceComparison(true);
        var dryRunContext = new DryRunContext();
        var activityLog = mock(ActivityLogService.class);
        when(scraper.scrape()).thenReturn(List.of(offer(OfferSource.UNKNOWNS)));
        doAnswer(invocation -> {
            assertThat(dryRunContext.active()).isFalse();
            return true;
        }).when(notifier).sendSystemCheck(true, """
                Commit: 0123456
                unknowns.de: ✅
                brettspiel-angebote.de: ✅""");

        new StartupSystemCheck(
                List.of(scraper),
                notifier,
                TestProperties.create(),
                priceComparison,
                dryRunContext,
                new ScraperExecutionCoordinator(), activityLog, true)
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(true, """
                Commit: 0123456
                unknowns.de: ✅
                brettspiel-angebote.de: ✅""");
        verify(priceComparison).healthCheck();
        assertThat(dryRunContext.active()).isFalse();
        verify(activityLog).recordApplicationStarted("""
                Systemcheck erfolgreich
                Commit: 0123456
                unknowns.de: ✅
                brettspiel-angebote.de: ✅""", true);
    }

    @Test
    void recordsEveryStartupEvenWhenChecksAreDisabledAndDeliveryFails() {
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = scraper(OfferSource.MILAN);
        var priceComparison = mock(PriceComparisonService.class);

        new StartupSystemCheck(List.of(scraper), notifier, TestProperties.create(), priceComparison,
                new DryRunContext(), new ScraperExecutionCoordinator(), activityLog, false)
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(true, "Commit: 0123456\nSystemcheck deaktiviert");
        verify(activityLog).recordApplicationStarted("Commit: 0123456\nSystemcheck deaktiviert", false);
        org.mockito.Mockito.verifyNoInteractions(priceComparison);
        verify(scraper, org.mockito.Mockito.never()).scrape();
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
                new ScraperExecutionCoordinator(), mock(ActivityLogService.class), true)
                .run(mock(ApplicationArguments.class));

        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifier).sendSystemCheck(eq(false), message.capture());
        assertThat(message.getValue()).isEqualTo("""
                Commit: 0123456
                Spiele-Offensive: ✅
                Milan-Spiele: FEHLER – keine Ergebnisse
                BGG Market: FEHLER – Antwort ungültig
                brettspiel-angebote.de: ✅""");
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
                new ScraperExecutionCoordinator(), mock(ActivityLogService.class), true)
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(false, """
                Commit: 0123456
                Milan-Spiele: ✅
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
                new ScraperExecutionCoordinator(), mock(ActivityLogService.class), true)
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(false, """
                Commit: 0123456
                Keine Quellen aktiviert
                brettspiel-angebote.de: ✅""");
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
