package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
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
        var dryRunContext = new DryRunContext();
        when(scraper.scrape()).thenReturn(List.of(offer(OfferSource.UNKNOWNS)));
        doAnswer(invocation -> {
            assertThat(dryRunContext.active()).isTrue();
            return true;
        }).when(notifier).sendSystemCheck(true, "Commit: 0123456\nunknowns.de: 1 Ergebnis");

        new StartupSystemCheck(
                List.of(scraper),
                notifier,
                TestProperties.create(),
                dryRunContext,
                new ScraperExecutionCoordinator())
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(true, "Commit: 0123456\nunknowns.de: 1 Ergebnis");
        assertThat(dryRunContext.active()).isFalse();
    }

    @Test
    void checksEverySourceAndReportsEmptyAndFailedResults() {
        var successfulScraper = scraper(OfferSource.SPIELE_OFFENSIVE);
        var emptyScraper = scraper(OfferSource.MILAN);
        var failedScraper = scraper(OfferSource.BGG_MARKET);
        var notifier = mock(OfferNotifier.class);
        when(successfulScraper.scrape()).thenReturn(List.of(offer(OfferSource.SPIELE_OFFENSIVE)));
        when(emptyScraper.scrape()).thenReturn(List.of());
        when(failedScraper.scrape()).thenThrow(new IllegalStateException("Antwort ungültig"));

        new StartupSystemCheck(
                List.of(failedScraper, emptyScraper, successfulScraper),
                notifier,
                TestProperties.create(),
                new DryRunContext(),
                new ScraperExecutionCoordinator())
                .run(mock(ApplicationArguments.class));

        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifier).sendSystemCheck(eq(false), message.capture());
        assertThat(message.getValue()).isEqualTo("""
                Commit: 0123456
                Spiele-Offensive: 1 Ergebnis
                Milan-Spiele: FEHLER – keine Ergebnisse
                BGG Market: FEHLER – Antwort ungültig""");
    }

    @Test
    void reportsAnErrorWhenNoSourceIsEnabled() {
        var notifier = mock(OfferNotifier.class);

        new StartupSystemCheck(
                List.of(),
                notifier,
                TestProperties.create(),
                new DryRunContext(),
                new ScraperExecutionCoordinator())
                .run(mock(ApplicationArguments.class));

        verify(notifier).sendSystemCheck(false, "Commit: 0123456\nKeine Quellen aktiviert");
    }

    private OfferScraper scraper(OfferSource source) {
        var scraper = mock(OfferScraper.class);
        when(scraper.source()).thenReturn(source);
        return scraper;
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
