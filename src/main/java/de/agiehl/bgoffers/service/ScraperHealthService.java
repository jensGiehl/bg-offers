package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.ScraperHealthStatus;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.repository.ScraperHealthStatusRepository;
import de.agiehl.bgoffers.scraper.OfferScraper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ScraperHealthService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm 'Uhr'", Locale.GERMANY)
            .withZone(ZoneId.of("Europe/Berlin"));

    private final List<OfferScraper> scrapers;
    private final OfferRepository offerRepository;
    private final ScraperHealthStatusRepository healthRepository;
    private final OfferNotifier notifier;
    private final OfferProperties properties;
    private final Clock clock;

    @Autowired
    public ScraperHealthService(
            List<OfferScraper> scrapers,
            OfferRepository offerRepository,
            ScraperHealthStatusRepository healthRepository,
            OfferNotifier notifier,
            OfferProperties properties) {
        this(scrapers, offerRepository, healthRepository, notifier, properties, Clock.systemUTC());
    }

    ScraperHealthService(
            List<OfferScraper> scrapers,
            OfferRepository offerRepository,
            ScraperHealthStatusRepository healthRepository,
            OfferNotifier notifier,
            OfferProperties properties,
            Clock clock) {
        this.scrapers = List.copyOf(scrapers);
        this.offerRepository = offerRepository;
        this.healthRepository = healthRepository;
        this.notifier = notifier;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void verifySources() {
        var now = Instant.now(clock);
        scrapers.stream()
                .map(OfferScraper::source)
                .distinct()
                .forEach(source -> verifySource(source, now));
    }

    private void verifySource(OfferSource source, Instant now) {
        var status = healthRepository.findById(source)
                .orElseGet(() -> ScraperHealthStatus.start(source, now));
        var latestOffer = offerRepository.findFirstBySourceOrderByFirstSeenAtDesc(source);
        latestOffer.ifPresent(offer -> status.observeNewData(offer.getFirstSeenAt()));

        var maximumSilence = maximumSilence(source);
        if (!status.isAlertSent() && !status.referenceTime().plus(maximumSilence).isAfter(now)) {
            var message = alertMessage(source, maximumSilence, status, latestOffer.isPresent());
            if (notifier.sendHealthAlert(message)) {
                status.markAlertSent();
            }
        }
        healthRepository.save(status);
    }

    private Duration maximumSilence(OfferSource source) {
        var health = properties.sourceHealth();
        return switch (source) {
            case SPIELE_OFFENSIVE -> health.spieleOffensiveMaxSilence();
            case MILAN -> health.milanMaxSilence();
            case BGG_MARKET -> health.bggMarketMaxSilence();
            case UNKNOWNS -> health.unknownsMaxSilence();
        };
    }

    private String alertMessage(
            OfferSource source,
            Duration maximumSilence,
            ScraperHealthStatus status,
            boolean hasReceivedData) {
        var days = maximumSilence.toDays();
        var unit = days == 1 ? "Tag" : "Tagen";
        var timestamp = TIMESTAMP_FORMAT.format(status.referenceTime());
        if (hasReceivedData) {
            return "Von %s sind seit mindestens %d %s keine neuen Daten eingegangen. Letzte neue Daten: %s."
                    .formatted(source.getDisplayName(), days, unit, timestamp);
        }
        return "Von %s sind seit mindestens %d %s keine Daten eingegangen. Überwachung aktiv seit: %s."
                .formatted(source.getDisplayName(), days, unit, timestamp);
    }
}
