package de.agiehl.bgoffers.web;

import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.ActivityLogEntry;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.repository.ActivityLogRepository;
import de.agiehl.bgoffers.service.WeeklyReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:webtest;DB_CLOSE_DELAY=-1",
        "offers.schedule.initial-delay=24h",
        "offers.startup-system-check-enabled=false",
        "offers.commit-id=0123456789abcdef0123456789abcdef01234567",
        "offers.schedule.crawl-delay=24h"
})
@AutoConfigureMockMvc
class OfferWebIntegrationTest {

    private static final String UNKNOWNS_LOGO_URL = "https://unknowns.de/images/style-4/pageLogo.svg";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildProperties buildProperties;

    @Autowired
    private OfferRepository repository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private WeeklyReportService weeklyReportService;

    @ParameterizedTest
    @CsvSource({
            "2026-10-06T19:44:00Z, 06.10.2026 · 21:44, 06.10.2026 21:44",
            "2026-01-06T19:44:00Z, 06.01.2026 · 20:44, 06.01.2026 20:44"
    })
    void rendersActivityAndOfferTimesInBerlin(String timestamp, String activityTime, String offerTime) throws Exception {
        var occurredAt = Instant.parse(timestamp);
        var offer = repository.saveAndFlush(Offer.create(OfferSource.MILAN, OfferType.STANDARD,
                "Zeitzonentest " + timestamp, "https://shop.example/timezone/" + timestamp, occurredAt));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(offer, occurredAt));

        mockMvc.perform(get("/aktivitaeten"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("time[datetime=\"" + timestamp + "\"]").eachText())
                            .contains(activityTime);
                });
        mockMvc.perform(get("/angebote/{id}", offer.getId()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(Jsoup.parse(result.getResponse().getContentAsString())
                        .select(".timestamps strong").eachText()).containsExactly(offerTime, offerTime, offerTime));
    }

    @Test
    void persistsWeeklyDecisionsAndHonorsInclusiveStartAndExclusiveEnd() throws Exception {
        var from = Instant.parse("2027-01-03T17:00:00Z");
        var until = Instant.parse("2027-01-10T17:00:00Z");
        var withheld = repository.saveAndFlush(Offer.create(OfferSource.MILAN, OfferType.STANDARD,
                "Report-Test zurückgehalten", "https://shop.example/report-withheld", from));
        var sent = repository.saveAndFlush(Offer.create(OfferSource.UNKNOWNS, OfferType.FORUM_POST,
                "Report-Test versendet", "https://shop.example/report-sent", from));
        var excluded = repository.saveAndFlush(Offer.create(OfferSource.MILAN, OfferType.STANDARD,
                "Nächste Woche", "https://shop.example/report-next", until));
        var older = repository.saveAndFlush(Offer.create(OfferSource.MILAN, OfferType.STANDARD,
                "Letzte Woche", "https://shop.example/report-previous", from.minusSeconds(1)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(withheld, from));
        activityLogRepository.saveAndFlush(ActivityLogEntry.bestPriceWithheld(withheld, from.plusSeconds(1)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.bestPriceWithheld(withheld, from.plusSeconds(2)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(sent, from));
        activityLogRepository.saveAndFlush(ActivityLogEntry.bestPriceWithheld(sent, from.plusSeconds(3)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerSent(sent, until.minusSeconds(1)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(excluded, until));
        activityLogRepository.saveAndFlush(ActivityLogEntry.bestPriceWithheld(excluded, until));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(older, from.minusSeconds(1)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerSent(older, from.plusSeconds(1)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.telegramDelivery(true, from.plusSeconds(1)));

        var report = weeklyReportService.createReport(from, until);

        assertThat(report.found()).isEqualTo(2);
        assertThat(report.sent()).isEqualTo(1);
        assertThat(report.withheld()).containsExactly(new de.agiehl.bgoffers.domain.WeeklyReport.WithheldOffer(
                withheld.getName(), withheld.getSourceUrl()));
        mockMvc.perform(get("/aktivitaeten"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bestpreisgrenze nicht erreicht")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Angebot versendet")));
    }

    @Test
    void sortsOverviewByLastChangeDescending() throws Exception {
        var earlierChange = Offer.create(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Früher aktualisiert",
                "https://shop.example/earlier-change",
                Instant.parse("2026-09-25T10:00:00Z"));
        earlierChange.setLastSeenAt(Instant.parse("2026-09-27T12:00:00Z"));
        repository.saveAndFlush(earlierChange);

        var laterChange = Offer.create(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Später aktualisiert",
                "https://shop.example/later-change",
                Instant.parse("2026-09-26T10:00:00Z"));
        laterChange.setLastSeenAt(Instant.parse("2026-09-27T11:00:00Z"));
        repository.saveAndFlush(laterChange);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(result -> assertAppearsBefore(
                        result.getResponse().getContentAsString(),
                        "Später aktualisiert",
                        "Früher aktualisiert"));
        mockMvc.perform(get("/").queryParam("source", OfferSource.MILAN.name()))
                .andExpect(status().isOk())
                .andExpect(result -> assertAppearsBefore(
                        result.getResponse().getContentAsString(),
                        "Später aktualisiert",
                        "Früher aktualisiert"));
    }

    @Test
    void rendersOverviewAndOfferDetails() throws Exception {
        var now = Instant.parse("2026-09-24T10:00:00Z");
        var offer = Offer.create(
                OfferSource.SPIELE_OFFENSIVE,
                OfferType.GROUP_DEAL,
                "Test-Gruppendeal",
                "https://shop.example/offer",
                now);
        offer.setPrice(new BigDecimal("19.99"));
        offer.setAvailability("Noch verfügbar 7 von 20");
        offer.setAvailableQuantity(7);
        offer.setTotalQuantity(20);
        offer.setBggStatus(LookupStatus.FOUND);
        offer.setBggRating(new BigDecimal("7.80"));
        offer.setBggWantToBuy(120);
        offer.setBggWantInTrade(17);
        offer.setComparisonStatus(LookupStatus.FOUND);
        offer.setComparisonAvailablePrice(new BigDecimal("24.99"));
        offer.setComparisonBestPrice(new BigDecimal("16.50"));
        offer.setNotifiedAt(now);
        var saved = repository.saveAndFlush(offer);
        var missingOffer = Offer.create(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Zauberberg (deutsch) Würfelspiel",
                "https://shop.example/missing",
                now);
        missingOffer.setBggStatus(LookupStatus.NOT_FOUND);
        missingOffer.setComparisonStatus(LookupStatus.NOT_FOUND);
        repository.saveAndFlush(missingOffer);
        var bundleOffer = Offer.create(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Testspiel Bundle",
                "https://shop.example/bundle",
                now);
        bundleOffer.setBggStatus(LookupStatus.SKIPPED);
        bundleOffer.setComparisonStatus(LookupStatus.SKIPPED);
        var savedBundle = repository.saveAndFlush(bundleOffer);
        var unknownsOffer = Offer.create(
                OfferSource.UNKNOWNS,
                OfferType.FORUM_POST,
                "Unknowns-Schnäppchen",
                "https://unknowns.de/forum/thread/42-unknowns-schnaeppchen/",
                now);
        unknownsOffer.setBggStatus(LookupStatus.SKIPPED);
        unknownsOffer.setComparisonStatus(LookupStatus.SKIPPED);
        var savedUnknownsOffer = repository.saveAndFlush(unknownsOffer);
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(saved, now));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(savedBundle, now.plusSeconds(15)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.offerFound(savedUnknownsOffer, now.plusSeconds(20)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.lookupRetry(
                saved, "BoardGameGeek", 2, 3, now.plusSeconds(30)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.httpRetry(
                "https://www.milan-spiele.de/testspiel.html",
                "HTTP 500",
                2,
                3,
                now.plusSeconds(45)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.telegramDelivery(true,
                "🎲 Telegram-Testspiel <Sonderedition>\n🔗 Angebot (https://shop.example/telegram-offer)",
                now.plusSeconds(60)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.telegramDelivery(true, now.plusSeconds(61)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.telegramDelivery(false,
                "sendMessage: HTTP 403 – Forbidden: bot was blocked", now.plusSeconds(90)));
        activityLogRepository.saveAndFlush(ActivityLogEntry.applicationStarted(
                "Commit: 0123456\nMilan-Spiele: ✅", true, now.plusSeconds(120)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test-Gruppendeal")))
                .andExpect(result -> {
                    var footer = Jsoup.parse(result.getResponse().getContentAsString()).selectFirst("footer");
                    var version = footer.selectFirst("code");
                    var timestamp = footer.selectFirst("time");
                    assertThat(version.text()).isEqualTo("0123456");
                    assertThat(version.attr("title")).isEqualTo("0123456789abcdef0123456789abcdef01234567");
                    assertThat(timestamp.attr("datetime")).isEqualTo(buildProperties.getTime().toString());
                    assertThat(buildProperties.getTime()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
                    assertThat(timestamp.text()).matches("\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2} MES?Z");
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bestpreis")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("16,50 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"Meldung versendet\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"Meldung nicht versendet\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Nicht gefunden"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Nicht erforderlich"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-label=\"BoardGameGeek-Bewertung 7,8 von 10\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"" + UNKNOWNS_LOGO_URL + "\"")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .containsOnlyOnce("class=\"bgg-rating\""));
        mockMvc.perform(get("/angebote/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verfügbare Angebote")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Want to buy")));
        mockMvc.perform(get("/angebote/{id}", savedUnknownsOffer.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"" + UNKNOWNS_LOGO_URL + "\"")));
        mockMvc.perform(get("/aktivitaeten"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test-Gruppendeal")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Brettspiel-Angebote")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BoardGameGeek")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Versuch 2 von 3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HTTP 500")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Telegram-Nachricht versendet")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".telegram-note .activity-detail").eachText())
                            .contains("🎲 Telegram-Testspiel <Sonderedition> 🔗 Angebot (https://shop.example/telegram-offer)",
                                    "Nachrichteninhalt für diesen älteren Eintrag nicht verfügbar.");
                    assertThat(document.select(".telegram-note Sonderedition")).isEmpty();
                    assertThat(document.text()).doesNotContain("Die Nachricht wurde erfolgreich übermittelt.");
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HTTP 403 – Forbidden: bot was blocked")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Anwendung gestartet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Commit: 0123456")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Milan-Spiele: ✅")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"" + UNKNOWNS_LOGO_URL + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Nicht erforderlich"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("CHAT_ID"))));
        mockMvc.perform(get("/fehlende-treffer"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ohne BGG-Treffer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ohne Brettspiel-Angebote-Treffer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Zauberberg")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Zauberberg (deutsch) Würfelspiel"))));
    }

    private void assertAppearsBefore(String html, String first, String second) {
        assertThat(html).contains(first, second);
        assertThat(html.indexOf(first)).isLessThan(html.indexOf(second));
    }
}
