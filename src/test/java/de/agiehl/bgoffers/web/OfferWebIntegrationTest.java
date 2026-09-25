package de.agiehl.bgoffers.web;

import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.ActivityLogEntry;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.repository.ActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
        "offers.schedule.crawl-delay=24h"
})
@AutoConfigureMockMvc
class OfferWebIntegrationTest {

    private static final String UNKNOWNS_LOGO_URL = "https://unknowns.de/images/style-4/pageLogo.svg";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OfferRepository repository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

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
        activityLogRepository.saveAndFlush(ActivityLogEntry.telegramDelivery(true, now.plusSeconds(60)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test-Gruppendeal")))
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
}
