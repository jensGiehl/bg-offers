package de.agiehl.bgoffers.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OfferScheduler {

    private final OfferImportService importService;
    private final ExternalHealthCheckService externalHealthCheckService;
    private final ScraperHealthService scraperHealthService;

    public OfferScheduler(
            OfferImportService importService,
            ExternalHealthCheckService externalHealthCheckService,
            ScraperHealthService scraperHealthService) {
        this.importService = importService;
        this.externalHealthCheckService = externalHealthCheckService;
        this.scraperHealthService = scraperHealthService;
    }

    @Scheduled(
            initialDelayString = "${offers.schedule.initial-delay}",
            fixedDelayString = "${offers.schedule.crawl-delay}")
    public void collectOffers() {
        importService.importAll();
    }

    @Scheduled(
            initialDelayString = "${offers.schedule.initial-delay}",
            fixedDelayString = "${offers.schedule.lookup-delay:30s}")
    public void retryLookups() {
        importService.processPendingLookups();
    }

    @Scheduled(cron = "${offers.schedule.health-cron}", zone = "Europe/Berlin")
    public void verifyHealth() {
        externalHealthCheckService.verify();
        scraperHealthService.verifySources();
    }
}
