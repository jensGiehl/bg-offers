package de.agiehl.bgoffers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "activity_log",
        indexes = {
                @Index(name = "idx_activity_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_activity_type", columnList = "type")
        })
public class ActivityLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityType type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private Long offerId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private OfferSource source;

    @Column(length = 500)
    private String offerName;

    @Column(length = 2000)
    private String imageUrl;

    @Column(precision = 12, scale = 2)
    private BigDecimal previousPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private LookupStatus bggStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private LookupStatus comparisonStatus;

    @Column(length = 1000)
    private String detail;

    protected ActivityLogEntry() {
    }

    public static ActivityLogEntry offerFound(Offer offer, Instant occurredAt) {
        return forOffer(ActivityType.OFFER_FOUND, offer, null, occurredAt);
    }

    public static ActivityLogEntry priceChanged(Offer offer, BigDecimal previousPrice, Instant occurredAt) {
        return forOffer(ActivityType.PRICE_CHANGED, offer, previousPrice, occurredAt);
    }

    public static ActivityLogEntry telegramDelivery(boolean successful, Instant occurredAt) {
        var entry = new ActivityLogEntry();
        entry.type = successful ? ActivityType.TELEGRAM_SENT : ActivityType.TELEGRAM_FAILED;
        entry.occurredAt = occurredAt;
        return entry;
    }

    public static ActivityLogEntry lookupRetry(
            Offer offer,
            String target,
            int nextAttempt,
            int maximumAttempts,
            Instant occurredAt) {
        var entry = forOffer(ActivityType.LOOKUP_RETRY, offer, null, occurredAt);
        entry.detail = "%s: Versuch %d von %d nach einem technischen Fehler"
                .formatted(target, nextAttempt, maximumAttempts);
        return entry;
    }

    public static ActivityLogEntry httpRetry(
            String target,
            String reason,
            int nextAttempt,
            int maximumAttempts,
            Instant occurredAt) {
        var entry = new ActivityLogEntry();
        entry.type = ActivityType.HTTP_RETRY;
        entry.occurredAt = occurredAt;
        entry.detail = "%s: %s; Versuch %d von %d"
                .formatted(target, reason, nextAttempt, maximumAttempts);
        return entry;
    }

    private static ActivityLogEntry forOffer(
            ActivityType type,
            Offer offer,
            BigDecimal previousPrice,
            Instant occurredAt) {
        var entry = new ActivityLogEntry();
        entry.type = type;
        entry.occurredAt = occurredAt;
        entry.offerId = offer.getId();
        entry.source = offer.getSource();
        entry.offerName = offer.getName();
        entry.imageUrl = offer.getImageUrl();
        entry.previousPrice = previousPrice;
        entry.currentPrice = offer.getPrice();
        entry.bggStatus = offer.getBggStatus();
        entry.comparisonStatus = offer.getComparisonStatus();
        return entry;
    }

    public Long getId() {
        return id;
    }

    public ActivityType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getOfferId() {
        return offerId;
    }

    public OfferSource getSource() {
        return source;
    }

    public String getOfferName() {
        return offerName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public LookupStatus getBggStatus() {
        return bggStatus;
    }

    public LookupStatus getComparisonStatus() {
        return comparisonStatus;
    }

    public String getDetail() {
        return detail;
    }
}
