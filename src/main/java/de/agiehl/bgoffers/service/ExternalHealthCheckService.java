package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.domain.ExternalHealthCheck;
import de.agiehl.bgoffers.domain.ExternalHealthCheckStatus;
import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.ExternalHealthCheckStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalHealthCheckService {

    private static final String BGG_FAILURE =
            "Die tägliche Suche nach „Magical Athlete“ bei BoardGameGeek konnte keine Daten laden.";
    private static final String BGG_RECOVERY =
            "Die tägliche Suche nach „Magical Athlete“ bei BoardGameGeek kann wieder Daten laden.";

    private final BggLookupService bggLookupService;
    private final ExternalHealthCheckStatusRepository repository;
    private final OfferNotifier notifier;

    public ExternalHealthCheckService(
            BggLookupService bggLookupService,
            ExternalHealthCheckStatusRepository repository,
            OfferNotifier notifier) {
        this.bggLookupService = bggLookupService;
        this.repository = repository;
        this.notifier = notifier;
    }

    @Transactional
    public void verify() {
        recordResult(
                ExternalHealthCheck.BOARD_GAME_GEEK,
                bggLookupService.healthCheck(),
                BGG_FAILURE,
                BGG_RECOVERY);
    }

    private void recordResult(
            ExternalHealthCheck check,
            boolean successful,
            String failureMessage,
            String recoveryMessage) {
        var status = repository.findById(check).orElseGet(() -> ExternalHealthCheckStatus.start(check));
        if (!successful) {
            notifier.sendHealthAlert(failureMessage);
            status.markFailed();
        } else if (status.isRecoveryNotificationPending() && notifier.sendHealthRecovery(recoveryMessage)) {
            status.markRecoveryNotificationSent();
        }
        repository.save(status);
    }
}
