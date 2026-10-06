package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.enrichment.BggResult;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonResult;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonService;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.scraper.OfferScraper;
import de.agiehl.bgoffers.scraper.ScrapedOffer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lookup-progress;DB_CLOSE_DELAY=-1",
        "offers.schedule.initial-delay=24h",
        "offers.startup-system-check-enabled=false"
})
@AutoConfigureMockMvc
class LookupProgressIntegrationTest {

    @Autowired
    private OfferRepository repository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void persistsSearchProgressRendersDetailsAndContinuesAfterServiceRestart() throws Exception {
        var bgg = mock(BggLookupService.class);
        var comparison = mock(PriceComparisonService.class);
        var notifier = mock(OfferNotifier.class);
        var scraper = mock(OfferScraper.class);
        var now = Instant.parse("2026-10-06T12:00:00Z");
        when(scraper.source()).thenReturn(OfferSource.MILAN);
        when(scraper.scrape()).thenReturn(List.of(new ScrapedOffer(
                OfferSource.MILAN, OfferType.STANDARD, "Scythe (deutsch)", "https://shop.example/lookup-progress",
                null, new BigDecimal("35.00"), null, null, null)));
        when(bgg.lookup(any())).thenReturn(BggResult.withStatus(LookupStatus.NOT_FOUND));
        when(comparison.lookup(any(), any())).thenReturn(PriceComparisonResult.withStatus(LookupStatus.ERROR));
        when(notifier.sendOffer(any())).thenReturn(true);
        var service = new OfferImportService(List.of(scraper), repository, bgg, comparison, notifier,
                mock(ActivityLogService.class), TestProperties.create(), new GameNameNormalizer(),
                Clock.fixed(now, ZoneOffset.UTC));
        service.importAll();
        var saved = repository.findBySourceAndSourceUrl(OfferSource.MILAN, "https://shop.example/lookup-progress").orElseThrow();
        assertThat(saved.getBggSearchAttempts()).hasSize(1);
        assertThat(saved.getComparisonSearchAttempts()).hasSize(1);
        assertThat(saved.getBggSearchAttempts().getFirst().getSearchTerm()).isEqualTo("Scythe");
        assertThat(repository.findTop50ByNextLookupAtLessThanEqualOrderByNextLookupAtAsc(now)).hasSize(1);
        var html = mockMvc.perform(get("/angebote/{id}", saved.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Versuch 1 von 3", "Suchbegriff:", "Scythe", "06.10.2026 14:00:00",
                "Nächster Versuch:", "brettspiel-angebote.de");
        verify(notifier, never()).sendOffer(any());

        var restarted = new OfferImportService(List.of(), repository, bgg, comparison, notifier,
                mock(ActivityLogService.class), TestProperties.create(), new GameNameNormalizer(),
                Clock.fixed(now, ZoneOffset.UTC));
        restarted.processPendingLookups();
        restarted.processPendingLookups();
        restarted.processPendingLookups();
        var completed = repository.findById(saved.getId()).orElseThrow();
        assertThat(completed.getBggSearchAttempts()).hasSize(3);
        assertThat(completed.getComparisonSearchAttempts()).hasSize(3);
        assertThat(completed.getNextLookupAt()).isNull();
        assertThat(completed.getNotificationFingerprint()).isNotBlank();
        verify(notifier).sendOffer(any());
        var completedHtml = mockMvc.perform(get("/angebote/{id}", saved.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(completedHtml).contains("Versuch 3 von 3", "Alle Versuche abgeschlossen.")
                .doesNotContain("Nächster Versuch:");
    }
}
