package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.domain.ScraperHealthStatus;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.repository.ScraperHealthStatusRepository;
import de.agiehl.bgoffers.scraper.OfferScraper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScraperHealthServiceTest {

    @Test
    void sendsOneAlertUntilNewDataArrivesAndAllowsANewAlertAfterRecovery() {
        var source = OfferSource.MILAN;
        var scraper = scraper(source);
        var offers = mock(OfferRepository.class);
        var health = mock(ScraperHealthStatusRepository.class);
        var notifier = mock(OfferNotifier.class);
        var storedStatus = new AtomicReference<ScraperHealthStatus>();
        var oldOffer = offer(source, "2026-09-20T10:00:00Z");
        var recoveredOffer = offer(source, "2026-09-25T12:00:00Z");

        when(health.findById(source)).thenAnswer(invocation -> Optional.ofNullable(storedStatus.get()));
        when(health.save(org.mockito.ArgumentMatchers.any(ScraperHealthStatus.class)))
                .thenAnswer(invocation -> {
                    var status = invocation.getArgument(0, ScraperHealthStatus.class);
                    storedStatus.set(status);
                    return status;
                });
        when(offers.findFirstBySourceOrderByFirstSeenAtDesc(source))
                .thenReturn(Optional.of(oldOffer))
                .thenReturn(Optional.of(oldOffer))
                .thenReturn(Optional.of(recoveredOffer))
                .thenReturn(Optional.of(recoveredOffer));
        when(notifier.sendHealthAlert(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        service(List.of(scraper), offers, health, notifier, "2026-09-25T10:00:00Z").verifySources();
        service(List.of(scraper), offers, health, notifier, "2026-09-25T11:00:00Z").verifySources();
        service(List.of(scraper), offers, health, notifier, "2026-09-25T12:00:00Z").verifySources();
        service(List.of(scraper), offers, health, notifier, "2026-09-30T12:00:00Z").verifySources();

        verify(notifier, times(2)).sendHealthAlert(contains("Milan-Spiele"));
        assertThat(storedStatus.get().isAlertSent()).isTrue();
        assertThat(storedStatus.get().getLastNewDataAt()).isEqualTo(recoveredOffer.getFirstSeenAt());
    }

    @Test
    void waitsThirtyDaysForUnknowns() {
        var source = OfferSource.UNKNOWNS;
        var scraper = scraper(source);
        var offers = mock(OfferRepository.class);
        var health = mock(ScraperHealthStatusRepository.class);
        var notifier = mock(OfferNotifier.class);
        var status = ScraperHealthStatus.start(source, Instant.parse("2026-08-25T10:00:00Z"));

        when(health.findById(source)).thenReturn(Optional.of(status));
        when(offers.findFirstBySourceOrderByFirstSeenAtDesc(source)).thenReturn(Optional.empty());
        when(notifier.sendHealthAlert(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        service(List.of(scraper), offers, health, notifier, "2026-09-24T09:59:59Z").verifySources();

        verify(notifier, never()).sendHealthAlert(org.mockito.ArgumentMatchers.anyString());

        service(List.of(scraper), offers, health, notifier, "2026-09-24T10:00:00Z").verifySources();

        verify(notifier).sendHealthAlert(contains("30 Tagen"));
    }

    @Test
    void retriesTheAlertWhenTelegramDeliveryFails() {
        var source = OfferSource.SPIELE_OFFENSIVE;
        var scraper = scraper(source);
        var offers = mock(OfferRepository.class);
        var health = mock(ScraperHealthStatusRepository.class);
        var notifier = mock(OfferNotifier.class);
        var status = ScraperHealthStatus.start(source, Instant.parse("2026-09-20T10:00:00Z"));

        when(health.findById(source)).thenReturn(Optional.of(status));
        when(offers.findFirstBySourceOrderByFirstSeenAtDesc(source)).thenReturn(Optional.empty());
        when(notifier.sendHealthAlert(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        var service = service(List.of(scraper), offers, health, notifier, "2026-09-25T10:00:00Z");
        service.verifySources();
        service.verifySources();

        verify(notifier, times(2)).sendHealthAlert(contains("Spiele-Offensive"));
        assertThat(status.isAlertSent()).isFalse();
    }

    private ScraperHealthService service(
            List<OfferScraper> scrapers,
            OfferRepository offers,
            ScraperHealthStatusRepository health,
            OfferNotifier notifier,
            String now) {
        return new ScraperHealthService(
                scrapers,
                offers,
                health,
                notifier,
                TestProperties.create(),
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private OfferScraper scraper(OfferSource source) {
        var scraper = mock(OfferScraper.class);
        when(scraper.source()).thenReturn(source);
        return scraper;
    }

    private Offer offer(OfferSource source, String firstSeenAt) {
        return Offer.create(
                source,
                OfferType.STANDARD,
                "Testspiel",
                "https://example.com/" + firstSeenAt,
                Instant.parse(firstSeenAt));
    }
}
