package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.domain.ActivityLogEntry;
import de.agiehl.bgoffers.domain.ActivityType;
import de.agiehl.bgoffers.domain.WeeklyReport;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WeeklyReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeeklyReportService.class);
    private static final ZoneId REPORT_ZONE = ZoneId.of("Europe/Berlin");
    private static final List<ActivityType> REPORT_TYPES = List.of(
            ActivityType.OFFER_FOUND, ActivityType.OFFER_SENT, ActivityType.BEST_PRICE_WITHHELD);

    private final ActivityLogRepository repository;
    private final OfferNotifier notifier;
    private final Clock clock;

    @Autowired
    public WeeklyReportService(ActivityLogRepository repository, OfferNotifier notifier) {
        this(repository, notifier, Clock.systemUTC());
    }

    WeeklyReportService(ActivityLogRepository repository, OfferNotifier notifier, Clock clock) {
        this.repository = repository;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(cron = "${offers.schedule.weekly-report-cron:0 0 18 * * SUN}", zone = "Europe/Berlin")
    public void sendWeeklyReport() {
        var now = Instant.now(clock).atZone(REPORT_ZONE);
        var until = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                .with(LocalTime.of(18, 0));
        if (until.isAfter(now)) {
            until = until.minusWeeks(1);
        }
        var report = createReport(until.minusWeeks(1).toInstant(), until.toInstant());
        if (!notifier.sendWeeklyReport(report)) {
            LOGGER.error("Wochenreport konnte nicht vollständig versendet werden");
        }
    }

    public WeeklyReport createReport(Instant from, Instant until) {
        var events = repository
                .findByTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                        REPORT_TYPES, from, until);
        var found = events.stream()
                .filter(event -> event.getType() == ActivityType.OFFER_FOUND)
                .map(ActivityLogEntry::getOfferId)
                .collect(Collectors.toSet());
        var sent = new HashSet<Long>();
        var withheld = new LinkedHashMap<Long, WeeklyReport.WithheldOffer>();
        for (var event : events) {
            if (!found.contains(event.getOfferId())) {
                continue;
            }
            if (event.getType() == ActivityType.OFFER_SENT) {
                sent.add(event.getOfferId());
            } else if (event.getType() == ActivityType.BEST_PRICE_WITHHELD) {
                withheld.put(event.getOfferId(), new WeeklyReport.WithheldOffer(
                        event.getOfferName(), event.getSourceUrl()));
            }
        }
        sent.forEach(withheld::remove);
        var offers = withheld.values().stream()
                .sorted(Comparator.comparing(WeeklyReport.WithheldOffer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new WeeklyReport(from, until, found.size(), sent.size(), offers);
    }
}
