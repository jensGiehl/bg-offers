package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.domain.ActivityLogEntry;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.WeeklyReport;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.ActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyReportServiceTest {

    @Test
    void countsOnlyNewOffersOnceAndExcludesPreviouslyWithheldOffersThatWereSent() {
        var from = Instant.parse("2026-10-04T16:00:00Z");
        var until = Instant.parse("2026-10-11T16:00:00Z");
        var repository = mock(ActivityLogRepository.class);
        var sent = offer(1L, "Später versendet");
        var withheld = offer(2L, "Zurückgehalten");
        var older = offer(3L, "Schon bekannt");
        var other = offer(4L, "Anderer Grund");
        var events = List.of(
                ActivityLogEntry.offerFound(sent, from),
                ActivityLogEntry.offerFound(withheld, from),
                ActivityLogEntry.offerFound(other, from),
                ActivityLogEntry.bestPriceWithheld(sent, from),
                ActivityLogEntry.bestPriceWithheld(withheld, from),
                ActivityLogEntry.bestPriceWithheld(withheld, from),
                ActivityLogEntry.bestPriceWithheld(older, from),
                ActivityLogEntry.offerSent(older, from),
                ActivityLogEntry.offerSent(sent, from),
                ActivityLogEntry.offerSent(sent, from));
        when(repository.findByTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                any(), eq(from), eq(until))).thenReturn(events);

        var report = new WeeklyReportService(repository, mock(OfferNotifier.class)).createReport(from, until);

        assertThat(report.found()).isEqualTo(3);
        assertThat(report.sent()).isEqualTo(1);
        assertThat(report.withheld()).containsExactly(
                new WeeklyReport.WithheldOffer("Zurückgehalten", "https://shop.example/2"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-11T16:00:02Z, 2026-10-04T16:00:00Z, 2026-10-11T16:00:00Z",
            "2026-03-29T16:00:02Z, 2026-03-22T17:00:00Z, 2026-03-29T16:00:00Z",
            "2026-10-25T17:00:02Z, 2026-10-18T16:00:00Z, 2026-10-25T17:00:00Z",
            "2026-10-11T15:59:59Z, 2026-09-27T16:00:00Z, 2026-10-04T16:00:00Z"
    })
    void reportsCompleteBerlinWeeksIncludingDaylightSavingChanges(Instant now, Instant from, Instant until) {
        var repository = mock(ActivityLogRepository.class);
        var notifier = mock(OfferNotifier.class);
        when(notifier.sendWeeklyReport(any())).thenReturn(true);
        var service = new WeeklyReportService(repository, notifier, Clock.fixed(now, ZoneOffset.UTC));

        service.sendWeeklyReport();

        verify(repository).findByTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                any(), eq(from), eq(until));
        var report = ArgumentCaptor.forClass(WeeklyReport.class);
        verify(notifier).sendWeeklyReport(report.capture());
        assertThat(report.getValue()).isEqualTo(new WeeklyReport(from, until, 0, 0, List.of()));
    }

    private Offer offer(long id, String name) {
        var offer = mock(Offer.class);
        when(offer.getId()).thenReturn(id);
        when(offer.getName()).thenReturn(name);
        when(offer.getSourceUrl()).thenReturn("https://shop.example/" + id);
        return offer;
    }
}
