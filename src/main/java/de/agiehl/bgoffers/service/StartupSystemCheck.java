package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.scraper.OfferScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "offers",
        name = "startup-system-check-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StartupSystemCheck implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSystemCheck.class);
    private static final int SHORT_COMMIT_LENGTH = 7;

    private final List<OfferScraper> scrapers;
    private final OfferNotifier notifier;
    private final OfferProperties properties;
    private final DryRunContext dryRunContext;
    private final ScraperExecutionCoordinator executionCoordinator;

    public StartupSystemCheck(
            List<OfferScraper> scrapers,
            OfferNotifier notifier,
            OfferProperties properties,
            DryRunContext dryRunContext,
            ScraperExecutionCoordinator executionCoordinator) {
        this.scrapers = scrapers.stream()
                .sorted(Comparator.comparingInt(scraper -> scraper.source().ordinal()))
                .toList();
        this.notifier = notifier;
        this.properties = properties;
        this.dryRunContext = dryRunContext;
        this.executionCoordinator = executionCoordinator;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        executionCoordinator.run(() -> dryRunContext.run(this::checkSources));
    }

    private void checkSources() {
        var results = scrapers.stream()
                .map(this::check)
                .toList();
        var successful = !results.isEmpty() && results.stream().allMatch(SourceCheck::successful);
        notifier.sendSystemCheck(successful, statusMessage(results));
        var sourceLabel = results.size() == 1 ? "Quelle" : "Quellen";
        if (successful) {
            LOGGER.info("Systemcheck für {} {} war erfolgreich", results.size(), sourceLabel);
        } else {
            LOGGER.error("Systemcheck für {} {} ist fehlgeschlagen", results.size(), sourceLabel);
        }
    }

    private SourceCheck check(OfferScraper scraper) {
        var source = scraper.source().getDisplayName();
        try {
            var offers = scraper.scrape();
            return offers.isEmpty()
                    ? SourceCheck.failed(source, "keine Ergebnisse")
                    : SourceCheck.successful(source, offers.size());
        } catch (RuntimeException exception) {
            LOGGER.error("Systemcheck für {} ist fehlgeschlagen: {}", source, exception.getMessage());
            return SourceCheck.failed(source, errorMessage(exception));
        }
    }

    private String statusMessage(List<SourceCheck> results) {
        var lines = new ArrayList<String>();
        lines.add("Commit: " + shortCommitId());
        if (results.isEmpty()) {
            lines.add("Keine Quellen aktiviert");
        } else {
            results.stream().map(SourceCheck::description).forEach(lines::add);
        }
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

    private record SourceCheck(String source, int resultCount, String error) {

        private static SourceCheck successful(String source, int resultCount) {
            return new SourceCheck(source, resultCount, null);
        }

        private static SourceCheck failed(String source, String error) {
            return new SourceCheck(source, 0, error);
        }

        private boolean successful() {
            return error == null && resultCount > 0;
        }

        private String description() {
            return successful()
                    ? "%s: %d %s".formatted(source, resultCount, resultCount == 1 ? "Ergebnis" : "Ergebnisse")
                    : "%s: FEHLER – %s".formatted(source, error);
        }
    }
}
