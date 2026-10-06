package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.scraper.OfferScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class StartupSystemCheck implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSystemCheck.class);
    private static final int SHORT_COMMIT_LENGTH = 7;

    private final List<OfferScraper> scrapers;
    private final OfferNotifier notifier;
    private final OfferProperties properties;
    private final PriceComparisonService priceComparisonService;
    private final DryRunContext dryRunContext;
    private final ScraperExecutionCoordinator executionCoordinator;
    private final ActivityLogService activityLogService;
    private final boolean checkEnabled;

    public StartupSystemCheck(
            List<OfferScraper> scrapers,
            OfferNotifier notifier,
            OfferProperties properties,
            PriceComparisonService priceComparisonService,
            DryRunContext dryRunContext,
            ScraperExecutionCoordinator executionCoordinator,
            ActivityLogService activityLogService,
            @Value("${offers.startup-system-check-enabled:true}") boolean checkEnabled) {
        this.scrapers = scrapers.stream()
                .sorted(Comparator.comparingInt(scraper -> scraper.source().ordinal()))
                .toList();
        this.notifier = notifier;
        this.properties = properties;
        this.priceComparisonService = priceComparisonService;
        this.dryRunContext = dryRunContext;
        this.executionCoordinator = executionCoordinator;
        this.activityLogService = activityLogService;
        this.checkEnabled = checkEnabled;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        executionCoordinator.run(this::checkSources);
    }

    private void checkSources() {
        var sourceResults = new ArrayList<CheckResult>();
        var results = new ArrayList<CheckResult>();
        if (checkEnabled) {
            dryRunContext.run(() -> {
                scrapers.stream().map(this::check).forEach(sourceResults::add);
                results.addAll(sourceResults);
                results.add(checkPriceComparison());
            });
        }
        var successful = !checkEnabled
                || (!sourceResults.isEmpty() && results.stream().allMatch(CheckResult::successful));
        var message = statusMessage(sourceResults.isEmpty(), results);
        var sent = notifier.sendSystemCheck(successful, message);
        var report = checkEnabled
                ? "Systemcheck %s\n%s".formatted(successful ? "erfolgreich" : "fehlgeschlagen", message)
                : message;
        activityLogService.recordApplicationStarted(report, sent);
        if (!checkEnabled) {
            LOGGER.info("Anwendung gestartet; Systemcheck deaktiviert");
            return;
        }
        var sourceLabel = sourceResults.size() == 1 ? "Quelle" : "Quellen";
        if (successful) {
            LOGGER.info("Systemcheck für {} {} und brettspiel-angebote.de war erfolgreich",
                    sourceResults.size(), sourceLabel);
        } else {
            LOGGER.error("Systemcheck für {} {} und brettspiel-angebote.de ist fehlgeschlagen",
                    sourceResults.size(), sourceLabel);
        }
    }

    private CheckResult check(OfferScraper scraper) {
        var source = scraper.source().getDisplayName();
        try {
            var offers = scraper.scrape();
            return offers.isEmpty()
                    ? CheckResult.failed(source, "keine Ergebnisse")
                    : CheckResult.successful(
                            source,
                            "%d %s".formatted(offers.size(), offers.size() == 1 ? "Ergebnis" : "Ergebnisse"));
        } catch (RuntimeException exception) {
            LOGGER.error("Systemcheck für {} ist fehlgeschlagen: {}", source, exception.getMessage());
            return CheckResult.failed(source, errorMessage(exception));
        }
    }

    private CheckResult checkPriceComparison() {
        try {
            return priceComparisonService.healthCheck()
                    ? CheckResult.successful("brettspiel-angebote.de", "Preisdaten für „Scythe“ gefunden")
                    : CheckResult.failed("brettspiel-angebote.de", "keine Preisdaten für „Scythe“");
        } catch (RuntimeException exception) {
            LOGGER.error("Systemcheck für brettspiel-angebote.de ist fehlgeschlagen: {}", exception.getMessage());
            return CheckResult.failed("brettspiel-angebote.de", errorMessage(exception));
        }
    }

    private String statusMessage(boolean noSourcesEnabled, List<CheckResult> results) {
        var lines = new ArrayList<String>();
        lines.add("Commit: " + shortCommitId());
        if (!checkEnabled) {
            lines.add("Systemcheck deaktiviert");
        } else if (noSourcesEnabled) {
            lines.add("Keine Quellen aktiviert");
        }
        results.stream().map(CheckResult::description).forEach(lines::add);
        return String.join("\n", lines);
    }

    private String shortCommitId() {
        var commitId = properties.commitId();
        if (commitId == null || commitId.isBlank() || commitId.equalsIgnoreCase("unknown")) {
            return "unbekannt";
        }
        var normalized = commitId.trim();
        return normalized.substring(0, Math.min(SHORT_COMMIT_LENGTH, normalized.length()));
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private record CheckResult(String target, String successMessage, String error) {

        private static CheckResult successful(String target, String successMessage) {
            return new CheckResult(target, successMessage, null);
        }

        private static CheckResult failed(String target, String error) {
            return new CheckResult(target, null, error);
        }

        private boolean successful() {
            return error == null;
        }

        private String description() {
            return successful()
                    ? "%s: %s".formatted(target, successMessage)
                    : "%s: FEHLER – %s".formatted(target, error);
        }
    }
}
