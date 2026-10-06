package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.domain.WeeklyReport;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportMessageTest {

    @Test
    void rendersCompactLinkedNamesAndEscapesHtml() {
        var report = report(4, 1, List.of(new WeeklyReport.WithheldOffer(
                "  Spiel <A> & B\n Edition  ", "https://shop.example/?a=1&b=\"2\"")));

        var messages = WeeklyReportMessage.html(report);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst())
                .contains("Neu: 4 · Versendet: 1 · Zurückgehalten: 1", "Sonstige nicht versendet: 2")
                .contains("• <a href=\"https://shop.example/?a=1&amp;b=&quot;2&quot;\">Spiel &lt;A&gt; &amp; B Edition</a>")
                .contains("04.10.2026 18:00 – 11.10.2026 18:00 (Berlin)");
        assertThat(WeeklyReportMessage.plainText(report)).contains("Spiel <A> & B Edition").doesNotContain("<a ");
    }

    @Test
    void splitsLargeReportsWithoutLosingLinksOrBreakingUnicode() {
        var offers = IntStream.range(0, 200)
                .mapToObj(index -> new WeeklyReport.WithheldOffer(
                        "Spiel " + index + " 🎲".repeat(100),
                        "https://shop.example/" + index + "?query=" + "a&".repeat(600)))
                .toList();

        var messages = WeeklyReportMessage.html(report(200, 0, offers));

        assertThat(messages.size()).isBetween(2, 15);
        var links = messages.stream().flatMap(message -> {
            var document = Jsoup.parse(message);
            assertThat(document.wholeText().length()).isLessThanOrEqualTo(4096);
            assertThat(document.wholeText()).doesNotContain("�");
            return document.select("a").stream();
        }).toList();
        assertThat(links).hasSize(200);
        for (var index = 0; index < offers.size(); index++) {
            assertThat(links.get(index).attr("href")).isEqualTo(offers.get(index).url());
            assertThat(links.get(index).text()).startsWith("Spiel " + index + " ").endsWith("…");
            assertThat(links.get(index).text().codePointCount(0, links.get(index).text().length()))
                    .isEqualTo(120);
        }
    }

    @Test
    void sendsEmptyReportsWithoutAnEmptyList() {
        var messages = WeeklyReportMessage.html(report(0, 0, List.of()));

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst()).contains("Neu: 0 · Versendet: 0 · Zurückgehalten: 0")
                .doesNotContain("•", "nicht erreicht", "Sonstige");
    }

    private WeeklyReport report(long found, long sent, List<WeeklyReport.WithheldOffer> withheld) {
        return new WeeklyReport(Instant.parse("2026-10-04T16:00:00Z"),
                Instant.parse("2026-10-11T16:00:00Z"), found, sent, withheld);
    }
}
