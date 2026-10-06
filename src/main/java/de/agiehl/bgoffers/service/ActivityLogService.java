package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.domain.ActivityLogEntry;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class ActivityLogService {

    private final ActivityLogRepository repository;
    private final DryRunContext dryRunContext;
    private final Clock clock;

    @Autowired
    public ActivityLogService(ActivityLogRepository repository, DryRunContext dryRunContext) {
        this(repository, dryRunContext, Clock.systemUTC());
    }

    ActivityLogService(ActivityLogRepository repository, DryRunContext dryRunContext, Clock clock) {
        this.repository = repository;
        this.dryRunContext = dryRunContext;
        this.clock = clock;
    }

    public void recordOfferFound(Offer offer, Instant occurredAt) {
        save(ActivityLogEntry.offerFound(offer, occurredAt));
    }

    public void recordPriceChanged(Offer offer, BigDecimal previousPrice, Instant occurredAt) {
        save(ActivityLogEntry.priceChanged(offer, previousPrice, occurredAt));
    }

    public void recordOfferSent(Offer offer, Instant occurredAt) {
        save(ActivityLogEntry.offerSent(offer, occurredAt));
    }

    public void recordBestPriceWithheld(Offer offer, Instant occurredAt) {
        save(ActivityLogEntry.bestPriceWithheld(offer, occurredAt));
    }

    public void recordLookupRetry(Offer offer, String target, int nextAttempt, int maximumAttempts) {
        save(ActivityLogEntry.lookupRetry(
                offer, target, nextAttempt, maximumAttempts, Instant.now(clock)));
    }

    public void recordHttpRetry(String target, String reason, int nextAttempt, int maximumAttempts) {
        save(ActivityLogEntry.httpRetry(
                target, reason, nextAttempt, maximumAttempts, Instant.now(clock)));
    }

    public void recordTelegramDelivery(boolean successful) {
        save(ActivityLogEntry.telegramDelivery(successful, Instant.now(clock)));
    }

    public void recordTelegramDelivery(boolean successful, String detail) {
        save(ActivityLogEntry.telegramDelivery(successful, detail, Instant.now(clock)));
    }

    public void recordApplicationStarted(String report, boolean notificationSent) {
        save(ActivityLogEntry.applicationStarted(report, notificationSent, Instant.now(clock)));
    }

    private void save(ActivityLogEntry entry) {
        if (!dryRunContext.active()) {
            repository.save(entry);
        }
    }
}
