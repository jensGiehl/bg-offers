package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.LookupTarget;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.enrichment.BggResult;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonResult;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.scraper.OfferScraper;
import de.agiehl.bgoffers.scraper.ScrapedOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OfferImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfferImportService.class);
    private static final Duration INITIAL_IMPORT_NOTIFICATION_PAUSE = Duration.ofHours(2);
    private static final BigDecimal BEST_PRICE_NOTIFICATION_FACTOR = new BigDecimal("1.10");

    private final List<OfferScraper> scrapers;
    private final OfferRepository repository;
    private final BggLookupService bggLookupService;
    private final PriceComparisonService priceComparisonService;
    private final OfferNotifier notifier;
    private final ActivityLogService activityLogService;
    private final OfferProperties properties;
    private final GameNameNormalizer normalizer;
    private final ScraperExecutionCoordinator executionCoordinator;
    private final Clock clock;
    private final Instant initialImportEndsAt;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public OfferImportService(
            List<OfferScraper> scrapers,
            OfferRepository repository,
            BggLookupService bggLookupService,
            PriceComparisonService priceComparisonService,
            OfferNotifier notifier,
            ActivityLogService activityLogService,
            OfferProperties properties,
            GameNameNormalizer normalizer,
            ScraperExecutionCoordinator executionCoordinator) {
        this(
                scrapers,
                repository,
                bggLookupService,
                priceComparisonService,
                notifier,
                activityLogService,
                properties,
                normalizer,
                Clock.systemUTC(),
                executionCoordinator);
    }

    OfferImportService(
            List<OfferScraper> scrapers,
            OfferRepository repository,
            BggLookupService bggLookupService,
            PriceComparisonService priceComparisonService,
            OfferNotifier notifier,
            ActivityLogService activityLogService,
            OfferProperties properties,
            GameNameNormalizer normalizer,
            Clock clock) {
        this(
                scrapers,
                repository,
                bggLookupService,
                priceComparisonService,
                notifier,
                activityLogService,
                properties,
                normalizer,
                clock,
                new ScraperExecutionCoordinator());
    }

    private OfferImportService(
            List<OfferScraper> scrapers,
            OfferRepository repository,
            BggLookupService bggLookupService,
            PriceComparisonService priceComparisonService,
            OfferNotifier notifier,
            ActivityLogService activityLogService,
            OfferProperties properties,
            GameNameNormalizer normalizer,
            Clock clock,
            ScraperExecutionCoordinator executionCoordinator) {
        this.scrapers = List.copyOf(scrapers);
        this.repository = repository;
        this.bggLookupService = bggLookupService;
        this.priceComparisonService = priceComparisonService;
        this.notifier = notifier;
        this.activityLogService = activityLogService;
        this.properties = properties;
        this.normalizer = normalizer;
        this.executionCoordinator = executionCoordinator;
        this.clock = clock;
        this.initialImportEndsAt = properties.initialImport()
                ? Instant.now(clock).plus(INITIAL_IMPORT_NOTIFICATION_PAUSE)
                : Instant.MIN;
    }

    public void importAll() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Angebotsabruf läuft bereits und wird nicht parallel gestartet");
            return;
        }
        try {
            executionCoordinator.run(() -> scrapers.forEach(this::importSource));
        } finally {
            running.set(false);
        }
    }

    public void processPendingLookups() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            var now = Instant.now(clock);
            for (var offer : repository.findTop50ByNextLookupAtLessThanEqualOrderByNextLookupAtAsc(now)) {
                try {
                    enrich(offer, offer.getBggId());
                } catch (RuntimeException exception) {
                    LOGGER.error("Ausstehende Recherche für {} ist fehlgeschlagen", offer.getName(), exception);
                }
            }
        } finally {
            running.set(false);
        }
    }

    private void importSource(OfferScraper scraper) {
        try {
            var offers = scraper.scrape();
            offers.forEach(this::importOffer);
            LOGGER.info("{} Angebote von {} verarbeitet", offers.size(), scraper.source().getDisplayName());
        } catch (RuntimeException exception) {
            LOGGER.error("Abruf von {} ist fehlgeschlagen: {}", scraper.source().getDisplayName(), exception.getMessage(), exception);
        }
    }

    private void importOffer(ScrapedOffer scraped) {
        var now = Instant.now(clock);
        var existing = findExisting(scraped);
        var offer = existing.orElseGet(() -> Offer.create(
                scraped.source(), scraped.type(), scraped.name(), scraped.sourceUrl(), now));
        var newOffer = existing.isEmpty();
        var previousPrice = offer.getPrice();
        var changedPrice = newOffer || !samePrice(previousPrice, scraped.price());

        offer.setName(scraped.name());
        offer.setSourceOfferId(scraped.sourceOfferId());
        offer.setImageUrl(scraped.imageUrl());
        offer.setPrice(scraped.price());
        offer.setAvailability(scraped.availability());
        offer.setAvailableQuantity(scraped.availableQuantity());
        offer.setTotalQuantity(scraped.totalQuantity());
        if (scraped.bggId() != null) {
            offer.setBggId(scraped.bggId());
        }
        offer.setLastSeenAt(now);
        if (changedPrice) {
            offer.setLastChangedAt(now);
        }
        offer = repository.save(offer);

        if (!changedPrice) {
            return;
        }
        if (offer.getType() == OfferType.SPIELESCHMIEDE
                || offer.getType() == OfferType.FORUM_POST
                || (offer.getSource() != OfferSource.BGG_MARKET && normalizer.isBundle(offer.getName()))) {
            skipEnrichment(offer, now);
            offer = repository.save(offer);
            notifyWhenRelevant(offer, now);
        } else {
            offer.resetLookupAttempts(now);
            offer = repository.save(offer);
            offer = enrich(offer, scraped.bggId());
        }
        if (newOffer) {
            activityLogService.recordOfferFound(offer, now);
        } else {
            activityLogService.recordPriceChanged(offer, previousPrice, now);
        }
    }

    private Offer enrich(Offer offer, Integer knownBggId) {
        if (offer.isBggLookupPending()) {
            var attempt = offer.getBggSearchAttempts().size() + 1;
            recordRetry(offer, "BoardGameGeek", attempt);
            var attemptedAt = Instant.now(clock);
            var result = knownBggId == null
                    ? bggLookupService.lookup(offer.getName())
                    : bggLookupService.lookupById(knownBggId);
            applyBggResult(offer, result);
            offer.recordLookupAttempt(LookupTarget.BGG,
                    knownBggId == null ? normalizer.searchTerm(offer.getName()) : "BGG-ID: " + knownBggId,
                    attemptedAt, result.status());
            offer = repository.save(offer);
        }
        if (offer.isComparisonLookupPending()) {
            var attempt = offer.getComparisonSearchAttempts().size() + 1;
            recordRetry(offer, "brettspiel-angebote.de", attempt);
            var attemptedAt = Instant.now(clock);
            var result = priceComparisonService.lookup(offer.getName(), offer.getBggId());
            applyComparisonResult(offer, result);
            offer.recordLookupAttempt(LookupTarget.COMPARISON,
                    normalizer.priceComparisonSearchTerm(offer.getName()), attemptedAt, result.status());
        }
        var now = Instant.now(clock);
        offer.setEnrichedAt(now);
        var pending = offer.isBggLookupPending() || offer.isComparisonLookupPending();
        offer.setNextLookupAt(pending ? now.plus(properties.http().lookupRetryDelay()) : null);
        offer = repository.save(offer);
        if (!pending) {
            notifyWhenRelevant(offer, now);
        }
        return offer;
    }

    private void recordRetry(Offer offer, String target, int attempt) {
        if (attempt > 1) {
            activityLogService.recordLookupRetry(offer, target, attempt, Offer.MAXIMUM_LOOKUP_ATTEMPTS);
        }
    }

    private void applyBggResult(Offer offer, BggResult bgg) {
        offer.setBggStatus(bgg.status());
        offer.setBggId(bgg.id());
        offer.setBggRating(bgg.rating());
        offer.setBggWantToBuy(bgg.wantToBuy());
        offer.setBggWantInTrade(bgg.wantInTrade());
    }

    private void applyComparisonResult(Offer offer, PriceComparisonResult comparison) {
        offer.setComparisonStatus(comparison.status());
        offer.setComparisonUrl(comparison.url());
        offer.setComparisonAvailablePrice(comparison.availablePrice());
        offer.setComparisonBestPrice(comparison.bestPrice());
    }

    private void skipEnrichment(Offer offer, Instant now) {
        applyBggResult(offer, BggResult.withStatus(LookupStatus.SKIPPED));
        applyComparisonResult(offer, PriceComparisonResult.withStatus(LookupStatus.SKIPPED));
        offer.setEnrichedAt(now);
        offer.setNextLookupAt(null);
    }

    private void notifyWhenRelevant(Offer offer, Instant now) {
        if (now.isBefore(initialImportEndsAt)) {
            offer.setNextLookupAt(initialImportEndsAt);
            repository.save(offer);
            return;
        }
        if (exceedsBestPriceLimit(offer)) {
            activityLogService.recordBestPriceWithheld(offer, now);
            return;
        }
        if (!shouldNotify(offer)) {
            return;
        }
        var fingerprint = fingerprint(offer);
        if (fingerprint.equals(offer.getNotificationFingerprint())) {
            return;
        }
        if (notifier.sendOffer(offer)) {
            offer.setNotificationFingerprint(fingerprint);
            offer.setNotifiedAt(now);
            repository.save(offer);
            activityLogService.recordOfferSent(offer, now);
        } else {
            offer.setNextLookupAt(now.plus(properties.http().lookupRetryDelay()));
            repository.save(offer);
        }
    }

    private boolean exceedsBestPriceLimit(Offer offer) {
        return offer.getSource() != OfferSource.UNKNOWNS
                && offer.getComparisonBestPrice() != null
                && (offer.getPrice() == null
                || offer.getPrice().compareTo(
                        offer.getComparisonBestPrice().multiply(BEST_PRICE_NOTIFICATION_FACTOR)) > 0);
    }

    private boolean shouldNotify(Offer offer) {
        if (offer.getSource() == OfferSource.UNKNOWNS
                || offer.getComparisonBestPrice() == null
                || offer.getType() == OfferType.SPIELESCHMIEDE || offer.getType() == OfferType.FORUM_POST) {
            return true;
        }
        var lookupMissing = offer.getBggStatus() != LookupStatus.FOUND
                || offer.getComparisonStatus() != LookupStatus.FOUND;
        var betterThanComparison = offer.getPrice() != null
                && offer.getComparisonAvailablePrice() != null
                && offer.getPrice().compareTo(offer.getComparisonAvailablePrice()) < 0;
        if (offer.getSource() == OfferSource.BGG_MARKET) {
            return betterThanComparison;
        }
        return lookupMissing || betterThanComparison;
    }

    private Optional<Offer> findExisting(ScrapedOffer scraped) {
        if (scraped.sourceOfferId() != null && !scraped.sourceOfferId().isBlank()) {
            return repository.findBySourceAndSourceOfferId(scraped.source(), scraped.sourceOfferId());
        }
        return repository.findBySourceAndSourceUrl(scraped.source(), scraped.sourceUrl());
    }

    private boolean samePrice(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private String fingerprint(Offer offer) {
        var material = String.join("|",
                offer.getSource().name(),
                offer.getSourceUrl(),
                Objects.toString(offer.getPrice(), "kein-preis"));
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar", exception);
        }
    }
}
