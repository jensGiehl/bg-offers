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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "offers",
        uniqueConstraints = @UniqueConstraint(name = "uk_offer_source_url", columnNames = {"source", "source_url"}),
        indexes = {
                @Index(name = "idx_offer_last_seen", columnList = "last_seen_at"),
                @Index(name = "idx_offer_last_changed", columnList = "last_changed_at"),
                @Index(name = "idx_offer_bgg_status", columnList = "bgg_status"),
                @Index(name = "idx_offer_comparison_status", columnList = "comparison_status")
        })
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferType type;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "source_url", nullable = false, length = 1500)
    private String sourceUrl;

    @Column(length = 2000)
    private String imageUrl;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 1000)
    private String availability;

    private Integer availableQuantity;

    private Integer totalQuantity;

    private Integer bggId;

    @Column(precision = 5, scale = 2)
    private BigDecimal bggRating;

    private Integer bggWantToBuy;

    private Integer bggWantInTrade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LookupStatus bggStatus = LookupStatus.NOT_REQUESTED;

    @Column(length = 1500)
    private String comparisonUrl;

    @Column(precision = 12, scale = 2)
    private BigDecimal comparisonAvailablePrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal comparisonBestPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LookupStatus comparisonStatus = LookupStatus.NOT_REQUESTED;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_changed_at", nullable = false)
    private Instant lastChangedAt;

    private Instant enrichedAt;

    @Column(length = 1000)
    private String notificationFingerprint;

    private Instant notifiedAt;

    protected Offer() {
    }

    public static Offer create(OfferSource source, OfferType type, String name, String sourceUrl, Instant now) {
        var offer = new Offer();
        offer.source = source;
        offer.type = type;
        offer.name = name;
        offer.sourceUrl = sourceUrl;
        offer.firstSeenAt = now;
        offer.lastSeenAt = now;
        offer.lastChangedAt = now;
        return offer;
    }

    public Long getId() {
        return id;
    }

    public OfferSource getSource() {
        return source;
    }

    public OfferType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getBggId() {
        return bggId;
    }

    public void setBggId(Integer bggId) {
        this.bggId = bggId;
    }

    public BigDecimal getBggRating() {
        return bggRating;
    }

    public void setBggRating(BigDecimal bggRating) {
        this.bggRating = bggRating;
    }

    public Integer getBggWantToBuy() {
        return bggWantToBuy;
    }

    public void setBggWantToBuy(Integer bggWantToBuy) {
        this.bggWantToBuy = bggWantToBuy;
    }

    public Integer getBggWantInTrade() {
        return bggWantInTrade;
    }

    public void setBggWantInTrade(Integer bggWantInTrade) {
        this.bggWantInTrade = bggWantInTrade;
    }

    public LookupStatus getBggStatus() {
        return bggStatus;
    }

    public void setBggStatus(LookupStatus bggStatus) {
        this.bggStatus = bggStatus;
    }

    public String getComparisonUrl() {
        return comparisonUrl;
    }

    public void setComparisonUrl(String comparisonUrl) {
        this.comparisonUrl = comparisonUrl;
    }

    public BigDecimal getComparisonAvailablePrice() {
        return comparisonAvailablePrice;
    }

    public void setComparisonAvailablePrice(BigDecimal comparisonAvailablePrice) {
        this.comparisonAvailablePrice = comparisonAvailablePrice;
    }

    public BigDecimal getComparisonBestPrice() {
        return comparisonBestPrice;
    }

    public void setComparisonBestPrice(BigDecimal comparisonBestPrice) {
        this.comparisonBestPrice = comparisonBestPrice;
    }

    public LookupStatus getComparisonStatus() {
        return comparisonStatus;
    }

    public void setComparisonStatus(LookupStatus comparisonStatus) {
        this.comparisonStatus = comparisonStatus;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getLastChangedAt() {
        return lastChangedAt;
    }

    public void setLastChangedAt(Instant lastChangedAt) {
        this.lastChangedAt = lastChangedAt;
    }

    public Instant getEnrichedAt() {
        return enrichedAt;
    }

    public void setEnrichedAt(Instant enrichedAt) {
        this.enrichedAt = enrichedAt;
    }

    public String getNotificationFingerprint() {
        return notificationFingerprint;
    }

    public void setNotificationFingerprint(String notificationFingerprint) {
        this.notificationFingerprint = notificationFingerprint;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(Instant notifiedAt) {
        this.notifiedAt = notifiedAt;
    }
}
