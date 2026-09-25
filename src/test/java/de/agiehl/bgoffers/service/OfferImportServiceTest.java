package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.enrichment.BggResult;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import de.agiehl.bgoffers.enrichment.PriceComparisonResult;
import de.agiehl.bgoffers.enrichment.PriceComparisonService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.scraper.OfferScraper;
import de.agiehl.bgoffers.scraper.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferImportServiceTest {

    @Test
    void enrichesAndNotifiesOnlyForNewOrChangedPrices() {
        var repository = mock(OfferRepository.class);
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = mock(OfferScraper.class);
        var stored = new AtomicReference<Offer>();
        var first = scraped("19.99");
        var changed = scraped("17.99");

        when(scraper.source()).thenReturn(OfferSource.MILAN);
        when(scraper.scrape())
                .thenReturn(List.of(first))
                .thenReturn(List.of(first))
                .thenReturn(List.of(changed));
        when(repository.findBySourceAndSourceUrl(OfferSource.MILAN, first.sourceUrl()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(Offer.class))).thenAnswer(invocation -> {
            var offer = invocation.getArgument(0, Offer.class);
            stored.set(offer);
            return offer;
        });
        when(bgg.lookup(first.name())).thenReturn(
                new BggResult(LookupStatus.FOUND, 42, new BigDecimal("7.8"), 120, 17));
        when(comparison.lookup(first.name(), 42)).thenReturn(
                new PriceComparisonResult(LookupStatus.FOUND, "https://compare.example/testspiel", new BigDecimal("24.99"), new BigDecimal("16.50")));
        when(notifier.sendOffer(any(Offer.class))).thenReturn(true);
        var service = new OfferImportService(
                List.of(scraper), repository, bgg, comparison, notifier,
                activityLog,
                de.agiehl.bgoffers.TestProperties.create(),
                new GameNameNormalizer(),
                Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));

        service.importAll();
        service.importAll();
        service.importAll();

        verify(bgg, times(2)).lookup(first.name());
        verify(comparison, times(2)).lookup(first.name(), 42);
        verify(notifier, times(2)).sendOffer(any(Offer.class));
        verify(activityLog, times(1)).recordOfferFound(any(Offer.class), any(Instant.class));
        verify(activityLog, times(1)).recordPriceChanged(
                any(Offer.class), eq(new BigDecimal("19.99")), any(Instant.class));
    }

    @Test
    void pausesNotificationsForTwoHoursDuringInitialImport() {
        var repository = mock(OfferRepository.class);
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = mock(OfferScraper.class);
        var stored = new AtomicReference<Offer>();
        var first = scraped("19.99");
        var changed = scraped("17.99");
        var startedAt = Instant.parse("2026-09-24T10:00:00Z");
        var clock = mock(Clock.class);

        when(scraper.source()).thenReturn(OfferSource.MILAN);
        when(scraper.scrape())
                .thenReturn(List.of(first))
                .thenReturn(List.of(changed));
        when(repository.findBySourceAndSourceUrl(OfferSource.MILAN, first.sourceUrl()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(Offer.class))).thenAnswer(invocation -> {
            var offer = invocation.getArgument(0, Offer.class);
            stored.set(offer);
            return offer;
        });
        when(bgg.lookup(first.name())).thenReturn(
                new BggResult(LookupStatus.FOUND, 42, new BigDecimal("7.8"), 120, 17));
        when(comparison.lookup(first.name(), 42)).thenReturn(
                new PriceComparisonResult(
                        LookupStatus.FOUND,
                        "https://compare.example/testspiel",
                        new BigDecimal("24.99"),
                        new BigDecimal("16.50")));
        when(notifier.sendOffer(any(Offer.class))).thenReturn(true);
        when(clock.instant()).thenReturn(
                startedAt,
                startedAt.plus(Duration.ofMinutes(30)),
                startedAt.plus(Duration.ofHours(2)));
        var service = new OfferImportService(
                List.of(scraper), repository, bgg, comparison, notifier, activityLog,
                de.agiehl.bgoffers.TestProperties.create(true),
                new GameNameNormalizer(),
                clock);

        service.importAll();
        service.importAll();

        verify(bgg, times(2)).lookup(first.name());
        verify(comparison, times(2)).lookup(first.name(), 42);
        verify(activityLog).recordOfferFound(any(Offer.class), any(Instant.class));
        verify(activityLog).recordPriceChanged(
                any(Offer.class), eq(new BigDecimal("19.99")), any(Instant.class));
        verify(notifier).sendOffer(any(Offer.class));
        assertThat(stored.get().getNotificationFingerprint()).isNotBlank();
        assertThat(stored.get().getNotifiedAt()).isEqualTo(startedAt.plus(Duration.ofHours(2)));
    }

    @Test
    void retriesTechnicalLookupErrorsBeforeSendingTheNotification() {
        var repository = mock(OfferRepository.class);
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = mock(OfferScraper.class);
        var offer = scraped("19.99");

        when(scraper.source()).thenReturn(OfferSource.MILAN);
        when(scraper.scrape()).thenReturn(List.of(offer));
        when(repository.findBySourceAndSourceUrl(OfferSource.MILAN, offer.sourceUrl()))
                .thenReturn(Optional.empty());
        when(repository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bgg.lookup(offer.name()))
                .thenReturn(BggResult.withStatus(LookupStatus.ERROR))
                .thenReturn(BggResult.withStatus(LookupStatus.ERROR))
                .thenReturn(new BggResult(LookupStatus.FOUND, 42, new BigDecimal("7.8"), 120, 17));
        when(comparison.lookup(offer.name(), 42))
                .thenReturn(PriceComparisonResult.withStatus(LookupStatus.ERROR))
                .thenReturn(new PriceComparisonResult(
                        LookupStatus.FOUND,
                        "https://compare.example/testspiel",
                        new BigDecimal("24.99"),
                        null));
        when(notifier.sendOffer(any(Offer.class))).thenReturn(true);
        var service = new OfferImportService(
                List.of(scraper), repository, bgg, comparison, notifier, activityLog,
                de.agiehl.bgoffers.TestProperties.create(),
                new GameNameNormalizer(),
                Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));

        service.importAll();

        verify(bgg, times(3)).lookup(offer.name());
        verify(comparison, times(2)).lookup(offer.name(), 42);
        verify(activityLog).recordLookupRetry(any(Offer.class), eq("BoardGameGeek"), eq(2), eq(3));
        verify(activityLog).recordLookupRetry(any(Offer.class), eq("BoardGameGeek"), eq(3), eq(3));
        verify(activityLog).recordLookupRetry(any(Offer.class), eq("brettspiel-angebote.de"), eq(2), eq(3));
        var order = org.mockito.Mockito.inOrder(bgg, comparison, notifier);
        order.verify(bgg, times(3)).lookup(offer.name());
        order.verify(comparison, times(2)).lookup(offer.name(), 42);
        order.verify(notifier).sendOffer(any(Offer.class));
    }

    @Test
    void skipsBothLookupsForBundles() {
        var repository = mock(OfferRepository.class);
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = mock(OfferScraper.class);
        var scraped = new ScrapedOffer(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Scythe Bundle (deutsch)",
                "https://www.milan-spiele.de/scythe-bundle-p-1.html",
                null,
                new BigDecimal("79.99"),
                null,
                null,
                null);
        var stored = new AtomicReference<Offer>();

        when(scraper.source()).thenReturn(OfferSource.MILAN);
        when(scraper.scrape()).thenReturn(List.of(scraped));
        when(repository.findBySourceAndSourceUrl(OfferSource.MILAN, scraped.sourceUrl()))
                .thenReturn(Optional.empty());
        when(repository.save(any(Offer.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, Offer.class);
            stored.set(saved);
            return saved;
        });
        when(notifier.sendOffer(any(Offer.class))).thenReturn(true);
        var service = new OfferImportService(
                List.of(scraper), repository, bgg, comparison, notifier, activityLog,
                de.agiehl.bgoffers.TestProperties.create(),
                new GameNameNormalizer(),
                Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));

        service.importAll();

        verify(bgg, never()).lookup(any());
        verify(comparison, never()).lookup(any(), any());
        assertThat(stored.get().getBggStatus()).isEqualTo(LookupStatus.SKIPPED);
        assertThat(stored.get().getComparisonStatus()).isEqualTo(LookupStatus.SKIPPED);
    }

    @Test
    void storesAndNotifiesAForumPostWithoutLookupsOnlyOnce() {
        var repository = mock(OfferRepository.class);
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = mock(OfferScraper.class);
        var scraped = new ScrapedOffer(
                OfferSource.UNKNOWNS,
                OfferType.FORUM_POST,
                "Neuer Forumsbeitrag",
                "https://unknowns.de/forum/thread/42-neuer-forumsbeitrag/",
                null,
                null,
                null,
                null,
                null);
        var stored = new AtomicReference<Offer>();

        when(scraper.source()).thenReturn(OfferSource.UNKNOWNS);
        when(scraper.scrape()).thenReturn(List.of(scraped));
        when(repository.findBySourceAndSourceUrl(OfferSource.UNKNOWNS, scraped.sourceUrl()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(Offer.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, Offer.class);
            stored.set(saved);
            return saved;
        });
        when(notifier.sendOffer(any(Offer.class))).thenReturn(true);
        var service = new OfferImportService(
                List.of(scraper), repository, bgg, comparison, notifier, activityLog,
                de.agiehl.bgoffers.TestProperties.create(),
                new GameNameNormalizer(),
                Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));

        service.importAll();
        service.importAll();

        verify(bgg, never()).lookup(any());
        verify(comparison, never()).lookup(any(), any());
        verify(notifier, times(1)).sendOffer(any(Offer.class));
        verify(activityLog, times(1)).recordOfferFound(any(Offer.class), any(Instant.class));
        assertThat(stored.get().getName()).isEqualTo("Neuer Forumsbeitrag");
        assertThat(stored.get().getSourceUrl()).isEqualTo(scraped.sourceUrl());
        assertThat(stored.get().getPrice()).isNull();
        assertThat(stored.get().getBggStatus()).isEqualTo(LookupStatus.SKIPPED);
        assertThat(stored.get().getComparisonStatus()).isEqualTo(LookupStatus.SKIPPED);
    }

    @Test
    void identifiesBggMarketOffersByProductIdAndUsesTheKnownBggId() {
        var repository = mock(OfferRepository.class);
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = mock(OfferScraper.class);
        var scraped = bggMarketOffer("4147596", "30.00");
        var stored = new AtomicReference<Offer>();

        when(scraper.source()).thenReturn(OfferSource.BGG_MARKET);
        when(scraper.scrape()).thenReturn(List.of(scraped));
        when(repository.findBySourceAndSourceOfferId(OfferSource.BGG_MARKET, "4147596"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(Offer.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, Offer.class);
            stored.set(saved);
            return saved;
        });
        when(bgg.lookupById(350458)).thenReturn(
                new BggResult(LookupStatus.FOUND, 350458, new BigDecimal("7.40"), 12, 3));
        when(comparison.lookup(scraped.name(), 350458)).thenReturn(
                new PriceComparisonResult(
                        LookupStatus.FOUND,
                        "https://www.brettspiel-angebote.de/testspiel",
                        new BigDecimal("34.99"),
                        new BigDecimal("29.99")));
        when(notifier.sendOffer(any(Offer.class))).thenReturn(true);
        var service = new OfferImportService(
                List.of(scraper), repository, bgg, comparison, notifier, activityLog,
                de.agiehl.bgoffers.TestProperties.create(),
                new GameNameNormalizer(),
                Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC));

        service.importAll();
        service.importAll();

        verify(repository, times(2)).findBySourceAndSourceOfferId(OfferSource.BGG_MARKET, "4147596");
        verify(repository, never()).findBySourceAndSourceUrl(OfferSource.BGG_MARKET, scraped.sourceUrl());
        verify(bgg).lookupById(350458);
        verify(comparison).lookup(scraped.name(), 350458);
        verify(notifier).sendOffer(any(Offer.class));
        assertThat(stored.get().getSourceOfferId()).isEqualTo("4147596");
        assertThat(stored.get().getBggId()).isEqualTo(350458);
    }

    @Test
    void doesNotNotifyForABggMarketOfferThatIsNotCheaperThanTheComparison() {
        var repository = mock(OfferRepository.class);
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var activityLog = mock(ActivityLogService.class);
        var scraper = mock(OfferScraper.class);
        var scraped = bggMarketOffer("4147596", "30.00");

        when(scraper.source()).thenReturn(OfferSource.BGG_MARKET);
        when(scraper.scrape()).thenReturn(List.of(scraped));
        when(repository.findBySourceAndSourceOfferId(OfferSource.BGG_MARKET, "4147596"))
                .thenReturn(Optional.empty());
        when(repository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bgg.lookupById(350458)).thenReturn(BggResult.withStatus(LookupStatus.NOT_CONFIGURED, 350458));
        when(comparison.lookup(scraped.name(), 350458)).thenReturn(
                new PriceComparisonResult(
                        LookupStatus.FOUND,
                        "https://www.brettspiel-angebote.de/testspiel",
                        new BigDecimal("29.99"),
                        new BigDecimal("25.00")));
        var service = new OfferImportService(
                List.of(scraper), repository, bgg, comparison, notifier, activityLog,
                de.agiehl.bgoffers.TestProperties.create(),
                new GameNameNormalizer(),
                Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC));

        service.importAll();

        verify(notifier, never()).sendOffer(any(Offer.class));
    }

    private ScrapedOffer bggMarketOffer(String productId, String price) {
        return new ScrapedOffer(
                OfferSource.BGG_MARKET,
                OfferType.STANDARD,
                "Terrakotta-Armee (German edition)",
                "https://boardgamegeek.com/market/product/" + productId,
                "https://cf.geekdo-images.com/terrakotta.jpg",
                new BigDecimal(price),
                null,
                null,
                null,
                productId,
                350458);
    }

    private ScrapedOffer scraped(String price) {
        return new ScrapedOffer(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Testspiel",
                "https://www.milan-spiele.de/testspiel-p-1.html",
                "https://www.milan-spiele.de/testspiel.jpg",
                new BigDecimal(price),
                "sofort lieferbar",
                null,
                null);
    }
}
