package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.enrichment.BggResult;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonResult;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonService;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.scraper.OfferScraper;
import de.agiehl.bgoffers.scraper.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingLookupTest {

    @Test
    void waitsThreeMinutesBetweenAttemptsAndSendsOnceAfterThirdFailure() {
        var fixture = new Fixture(false);
        fixture.service.importAll();
        var firstAttempt = fixture.now;
        assertThat(fixture.offer.getNextLookupAt()).isEqualTo(firstAttempt.plusSeconds(180));
        fixture.advance(179);
        fixture.service.processPendingLookups();
        fixture.service.importAll();
        verify(fixture.bgg).lookup("Scythe (deutsch)");
        verify(fixture.notifier, never()).sendOffer(any());
        fixture.advance(1);
        fixture.service.processPendingLookups();
        verify(fixture.notifier, never()).sendOffer(any());
        fixture.advance(180);
        fixture.service.processPendingLookups();
        fixture.service.processPendingLookups();
        fixture.advance(3600);
        fixture.service.importAll();
        verify(fixture.bgg, times(3)).lookup("Scythe (deutsch)");
        verify(fixture.comparison, times(3)).lookup("Scythe (deutsch)", null);
        verify(fixture.notifier).sendOffer(any());
        assertThat(fixture.offer.getComparisonBestPrice()).isNull();
        assertThat(fixture.offer.getNextLookupAt()).isNull();
        assertThat(fixture.offer.getBggSearchAttempts()).extracting(attempt -> attempt.getSearchTerm())
                .containsExactly("Scythe", "Scythe", "Scythe");
        assertThat(fixture.offer.getComparisonSearchAttempts()).extracting(attempt -> attempt.getAttemptedAt())
                .containsExactly(firstAttempt, firstAttempt.plusSeconds(180), firstAttempt.plusSeconds(360));
    }

    @Test
    void stopsSuccessfulBggLookupAndRetriesMissingBestPrice() {
        var fixture = new Fixture(false);
        when(fixture.bgg.lookup(any())).thenReturn(new BggResult(LookupStatus.FOUND, 42, null, null, null));
        when(fixture.comparison.lookup(any(), any())).thenReturn(new PriceComparisonResult(
                LookupStatus.FOUND, "https://compare.example/scythe", new BigDecimal("40.00"), null));
        fixture.service.importAll();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        verify(fixture.bgg).lookup(any());
        verify(fixture.bgg, never()).lookupById(42);
        verify(fixture.comparison, times(3)).lookup("Scythe (deutsch)", 42);
        verify(fixture.notifier).sendOffer(any());
    }

    @Test
    void sendsAsSoonAsBothLookupsSucceedWithoutUsingAllAttempts() {
        var fixture = new Fixture(false);
        when(fixture.bgg.lookup(any())).thenReturn(new BggResult(LookupStatus.FOUND, 42, null, null, null));
        when(fixture.comparison.lookup(any(), any()))
                .thenReturn(PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND))
                .thenReturn(new PriceComparisonResult(LookupStatus.FOUND, "https://compare.example/scythe",
                        new BigDecimal("40.00"), new BigDecimal("29.00")));
        fixture.service.importAll();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        verify(fixture.bgg).lookup(any());
        verify(fixture.comparison, times(2)).lookup(any(), any());
        verify(fixture.notifier).sendOffer(any());
        assertThat(fixture.offer.getNextLookupAt()).isNull();
    }

    @Test
    void retriesFailedDeliveryWithoutRestartingExhaustedLookups() {
        var fixture = new Fixture(false);
        when(fixture.notifier.sendOffer(any())).thenReturn(false, true);
        fixture.service.importAll();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        assertThat(fixture.offer.getNotifiedAt()).isNull();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        fixture.service.processPendingLookups();
        verify(fixture.bgg, times(3)).lookup(any());
        verify(fixture.comparison, times(3)).lookup(any(), any());
        verify(fixture.notifier, times(2)).sendOffer(any());
        assertThat(fixture.offer.getNotifiedAt()).isEqualTo(fixture.now);
        assertThat(fixture.offer.getNextLookupAt()).isNull();
    }

    @Test
    void honorsInitialImportPauseAndEventuallySendsWithoutPriceChange() {
        var fixture = new Fixture(true);
        fixture.service.importAll();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        verify(fixture.notifier, never()).sendOffer(any());
        fixture.advance(6840);
        fixture.service.processPendingLookups();
        verify(fixture.notifier).sendOffer(any());
        verify(fixture.bgg, times(3)).lookup(any());
    }

    @Test
    void startsNewAttemptsForAChangedPrice() {
        var fixture = new Fixture(false);
        fixture.service.importAll();
        fixture.advance(180);
        fixture.service.processPendingLookups();
        fixture.advance(60);
        when(fixture.scraper.scrape()).thenReturn(List.of(fixture.scraped("25.00")));
        fixture.service.importAll();
        assertThat(fixture.offer.getBggSearchAttempts()).hasSize(1);
        assertThat(fixture.offer.getComparisonSearchAttempts()).hasSize(1);
        assertThat(fixture.offer.getNextLookupAt()).isEqualTo(fixture.now.plusSeconds(180));
        verify(fixture.notifier, never()).sendOffer(any());
    }

    private static class Fixture {

        private final OfferRepository repository = mock(OfferRepository.class);
        private final BggLookupService bgg = mock(BggLookupService.class);
        private final PriceComparisonService comparison = mock(PriceComparisonService.class);
        private final OfferNotifier notifier = mock(OfferNotifier.class);
        private final OfferScraper scraper = mock(OfferScraper.class);
        private final Clock clock = mock(Clock.class);
        private final OfferImportService service;
        private Instant now = Instant.parse("2026-10-06T12:00:00Z");
        private Offer offer;

        private Fixture(boolean initialImport) {
            when(clock.instant()).thenAnswer(_ -> now);
            when(scraper.source()).thenReturn(OfferSource.MILAN);
            when(scraper.scrape()).thenReturn(List.of(scraped("30.00")));
            when(repository.findBySourceAndSourceUrl(any(), any())).thenAnswer(_ -> Optional.ofNullable(offer));
            when(repository.save(any())).thenAnswer(invocation -> {
                offer = invocation.getArgument(0);
                return offer;
            });
            when(repository.findTop50ByNextLookupAtLessThanEqualOrderByNextLookupAtAsc(any()))
                    .thenAnswer(_ -> offer != null && offer.getNextLookupAt() != null
                            && !offer.getNextLookupAt().isAfter(now) ? List.of(offer) : List.of());
            when(bgg.lookup(any())).thenReturn(BggResult.withStatus(LookupStatus.ERROR));
            when(comparison.lookup(any(), any())).thenReturn(PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND));
            when(notifier.sendOffer(any())).thenReturn(true);
            var base = TestProperties.create(initialImport);
            var properties = new OfferProperties(base.sources(), new OfferProperties.Http(
                    base.http().timeout(), base.http().userAgent(), 8, Duration.ZERO, Duration.ofMinutes(3), 2),
                    base.schedule(), base.sourceHealth(), base.initialImport(), base.commitId(), base.telegram(), base.bgg());
            service = new OfferImportService(List.of(scraper), repository, bgg, comparison, notifier,
                    mock(ActivityLogService.class), properties, new GameNameNormalizer(), clock);
        }

        private ScrapedOffer scraped(String price) {
            return new ScrapedOffer(OfferSource.MILAN, OfferType.STANDARD, "Scythe (deutsch)",
                    "https://shop.example/scythe", null, new BigDecimal(price), null, null, null);
        }

        private void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }
}
