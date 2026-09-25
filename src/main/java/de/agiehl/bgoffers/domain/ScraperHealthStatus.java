package de.agiehl.bgoffers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "scraper_health")
public class ScraperHealthStatus {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferSource source;

    @Column(name = "monitoring_started_at", nullable = false)
    private Instant monitoringStartedAt;

    @Column(name = "last_new_data_at")
    private Instant lastNewDataAt;

    @Column(name = "alert_sent", nullable = false)
    private boolean alertSent;

    protected ScraperHealthStatus() {
    }

    public static ScraperHealthStatus start(OfferSource source, Instant now) {
        var status = new ScraperHealthStatus();
        status.source = source;
        status.monitoringStartedAt = now;
        return status;
    }

    public OfferSource getSource() {
        return source;
    }

    public Instant getMonitoringStartedAt() {
        return monitoringStartedAt;
    }

    public Instant getLastNewDataAt() {
        return lastNewDataAt;
    }

    public boolean isAlertSent() {
        return alertSent;
    }

    public void observeNewData(Instant observedAt) {
        if (lastNewDataAt == null || observedAt.isAfter(lastNewDataAt)) {
            lastNewDataAt = observedAt;
            alertSent = false;
        }
    }

    public Instant referenceTime() {
        return lastNewDataAt == null ? monitoringStartedAt : lastNewDataAt;
    }

    public void markAlertSent() {
        alertSent = true;
    }
}
