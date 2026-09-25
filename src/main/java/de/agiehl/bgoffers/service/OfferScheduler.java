package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.enrichment.PriceComparisonService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OfferScheduler {

    private final OfferImportService importService;
    private final PriceComparisonService priceComparisonService;
    private final OfferNotifier notifier;

    public OfferScheduler(
            OfferImportService importService,
            PriceComparisonService priceComparisonService,
            OfferNotifier notifier) {
        this.importService = importService;
        this.priceComparisonService = priceComparisonService;
        this.notifier = notifier;
    }

    @Scheduled(
            initialDelayString = "${offers.schedule.initial-delay}",
            fixedDelayString = "${offers.schedule.crawl-delay}")
    public void collectOffers() {
        importService.importAll();
    }

    @Scheduled(cron = "${offers.schedule.health-cron}", zone = "Europe/Berlin")
    public void verifyPriceComparison() {
        if (!priceComparisonService.healthCheck()) {
            notifier.sendHealthAlert("Die tägliche Suche nach „Scythe“ auf brettspiel-angebote.de konnte keine Preisdaten laden.");
        }
    }
}
