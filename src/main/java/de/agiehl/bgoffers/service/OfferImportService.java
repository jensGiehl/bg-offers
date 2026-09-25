package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.enrichment.BggResult;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import de.agiehl.bgoffers.enrichment.PriceComparisonService;
import de.agiehl.bgoffers.enrichment.PriceComparisonResult;
import de.agiehl.bgoffers.config.OfferProperties;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OfferImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfferImportService.class);

    private final List<OfferScraper> scrapers;
    private final OfferRepository repository;
    private final BggLookupService bggLookupService;
    private final PriceComparisonService priceComparisonService;
    private final OfferNotifier notifier;
    private final ActivityLogService activityLogService;
    private final OfferProperties properties;
    private final GameNameNormalizer normalizer;
    private final Clock clock;
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
            GameNameNormalizer normalizer) {
        this(
                scrapers,
                repository,
                bggLookupService,
                priceComparisonService,
                notifier,
                activityLogService,
                properties,
                normalizer,
                Clock.systemUTC());
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
        this.scrapers = List.copyOf(scrapers);
        this.repository = repository;
        this.bggLookupService = bggLookupService;
        this.priceComparisonService = priceComparisonService;
        this.notifier = notifier;
        this.activityLogService = activityLogService;
        this.properties = properties;
        this.normalizer = normalizer;
        this.clock = clock;
    }

    public void importAll() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Angebotsabruf läuft bereits und wird nicht parallel gestartet");
            return;
        }
        try {
            for (var scraper : scrapers) {
                importSource(scraper);
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
        var existing = repository.findBySourceAndSourceUrl(scraped.source(), scraped.sourceUrl());
        var offer = existing.orElseGet(() -> Offer.create(
                scraped.source(), scraped.type(), scraped.name(), scraped.sourceUrl(), now));
        var newOffer = existing.isEmpty();
        var previousPrice = offer.getPrice();
        var changedPrice = newOffer || !samePrice(previousPrice, scraped.price());

        offer.setName(scraped.name());
        offer.setImageUrl(scraped.imageUrl());
        offer.setPrice(scraped.price());
        offer.setAvailability(scraped.availability());
        offer.setAvailableQuantity(scraped.availableQuantity());
        offer.setTotalQuantity(scraped.totalQuantity());
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
                || normalizer.isBundle(offer.getName())) {
            skipEnrichment(offer, now);
        } else {
            enrich(offer, now);
        }
        offer = repository.save(offer);
        if (newOffer) {
            activityLogService.recordOfferFound(offer, now);
        } else {
            activityLogService.recordPriceChanged(offer, previousPrice, now);
        }
        notifyWhenRelevant(offer, now);
    }

    private void enrich(Offer offer, Instant now) {
        var bgg = lookupBggWithRetries(offer);
        applyBggResult(offer, bgg);

        var comparison = lookupComparisonWithRetries(offer, bgg.id());
        applyComparisonResult(offer, comparison);
        offer.setEnrichedAt(now);
    }

    private BggResult lookupBggWithRetries(Offer offer) {
        var result = BggResult.withStatus(LookupStatus.ERROR);
        var maximumAttempts = Math.max(1, properties.http().maxAttempts());
        for (var attempt = 1; attempt <= maximumAttempts; attempt++) {
            result = bggLookupService.lookup(offer.getName());
            applyBggResult(offer, result);
            if (result.status() != LookupStatus.ERROR || attempt == maximumAttempts) {
                return result;
            }
            activityLogService.recordLookupRetry(
                    offer, "BoardGameGeek", attempt + 1, maximumAttempts);
            waitBeforeLookupRetry();
        }
        return result;
    }

    private PriceComparisonResult lookupComparisonWithRetries(Offer offer, Integer bggId) {
        var result = PriceComparisonResult.withStatus(LookupStatus.ERROR);
        var maximumAttempts = Math.max(1, properties.http().maxAttempts());
        for (var attempt = 1; attempt <= maximumAttempts; attempt++) {
            result = priceComparisonService.lookup(offer.getName(), bggId);
            applyComparisonResult(offer, result);
            if (result.status() != LookupStatus.ERROR || attempt == maximumAttempts) {
                return result;
            }
            activityLogService.recordLookupRetry(
                    offer, "brettspiel-angebote.de", attempt + 1, maximumAttempts);
            waitBeforeLookupRetry();
        }
        return result;
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
    }

    private void waitBeforeLookupRetry() {
        try {
            Thread.sleep(properties.http().retryDelay());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Warten auf erneute Recherche wurde unterbrochen", exception);
        }
    }

    private void notifyWhenRelevant(Offer offer, Instant now) {
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
        }
    }

    private boolean shouldNotify(Offer offer) {
        if (offer.getType() == OfferType.SPIELESCHMIEDE || offer.getType() == OfferType.FORUM_POST) {
            return true;
        }
        var lookupMissing = offer.getBggStatus() != LookupStatus.FOUND
                || offer.getComparisonStatus() != LookupStatus.FOUND;
        var betterThanComparison = offer.getPrice() != null
                && offer.getComparisonAvailablePrice() != null
                && offer.getPrice().compareTo(offer.getComparisonAvailablePrice()) < 0;
        return lookupMissing || betterThanComparison;
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
