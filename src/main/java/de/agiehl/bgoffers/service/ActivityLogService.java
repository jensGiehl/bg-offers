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
    private final Clock clock;

    @Autowired
    public ActivityLogService(ActivityLogRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ActivityLogService(ActivityLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void recordOfferFound(Offer offer, Instant occurredAt) {
        repository.save(ActivityLogEntry.offerFound(offer, occurredAt));
    }

    public void recordPriceChanged(Offer offer, BigDecimal previousPrice, Instant occurredAt) {
        repository.save(ActivityLogEntry.priceChanged(offer, previousPrice, occurredAt));
    }

    public void recordLookupRetry(Offer offer, String target, int nextAttempt, int maximumAttempts) {
        repository.save(ActivityLogEntry.lookupRetry(
                offer, target, nextAttempt, maximumAttempts, Instant.now(clock)));
    }

    public void recordHttpRetry(String target, String reason, int nextAttempt, int maximumAttempts) {
        repository.save(ActivityLogEntry.httpRetry(
                target, reason, nextAttempt, maximumAttempts, Instant.now(clock)));
    }

    public void recordTelegramDelivery(boolean successful) {
        repository.save(ActivityLogEntry.telegramDelivery(successful, Instant.now(clock)));
    }
}
