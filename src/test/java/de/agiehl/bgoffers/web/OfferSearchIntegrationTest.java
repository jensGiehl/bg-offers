package de.agiehl.bgoffers.web;

import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.repository.OfferRepository;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:searchtest;DB_CLOSE_DELAY=-1",
        "offers.schedule.initial-delay=24h",
        "offers.startup-system-check-enabled=false",
        "offers.schedule.crawl-delay=24h"
})
@AutoConfigureMockMvc
@Transactional
class OfferSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OfferRepository repository;

    @Test
    void searchesTitleFragmentsIgnoringCaseAndSurroundingWhitespace() throws Exception {
        saveOffer("Die Glasstraße", OfferSource.MILAN, 1);
        saveOffer("Glasstraße im Angebot", OfferSource.UNKNOWNS, 2);
        saveOffer("Anderes Spiel", OfferSource.MILAN, 3);

        mockMvc.perform(get("/").param("q", "  GLASSTRAßE  "))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".offer-card h2").eachText())
                            .containsExactly("Glasstraße im Angebot", "Die Glasstraße");
                    assertThat(document.selectFirst("#title-search").val()).isEqualTo("GLASSTRAßE");
                    assertThat(document.selectFirst(".search-panel p").text()).contains("2 Treffer");
                });

        mockMvc.perform(get("/").param("q", "glas").param("source", "MILAN"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".offer-card h2").eachText()).containsExactly("Die Glasstraße");
                    assertThat(document.selectFirst(".search-panel input[name=source]").val()).isEqualTo("MILAN");
                    assertThat(document.selectFirst(".search-panel a").attr("href")).isEqualTo("/?source=MILAN");
                });
    }

    @Test
    void keepsSearchAndSourceAcrossPaginationAndSourceChanges() throws Exception {
        for (var index = 0; index < 25; index++) {
            saveOffer("Suchspiel & Erweiterung " + index, OfferSource.MILAN, index);
        }
        saveOffer("Suchspiel & Erweiterung andere Quelle", OfferSource.BGG_MARKET, 26);
        saveOffer("Anderes Spiel", OfferSource.MILAN, 27);

        mockMvc.perform(get("/").param("q", "Suchspiel & Erweiterung").param("source", "MILAN"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".offer-card")).hasSize(24);
                    assertThat(document.selectFirst(".search-panel p").text()).contains("25 Treffer");
                    assertThat(document.select("nav[aria-label=Seitennavigation] a").last().attr("href"))
                            .isEqualTo("/?page=1&source=MILAN&q=Suchspiel%20%26%20Erweiterung");
                    assertThat(document.selectFirst(".filter-bar a").attr("href"))
                            .isEqualTo("/?q=Suchspiel%20%26%20Erweiterung");
                    assertThat(document.select(".filter-bar a").eachAttr("href"))
                            .contains("/?source=BGG_MARKET&q=Suchspiel%20%26%20Erweiterung");
                    assertThat(document.select(".search-panel input[name=page]")).isEmpty();
                });

        mockMvc.perform(get("/").param("q", "Suchspiel & Erweiterung").param("source", "MILAN").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".offer-card h2").eachText())
                            .containsExactly("Suchspiel & Erweiterung 0");
                    assertThat(document.select("nav[aria-label=Seitennavigation] a").first().attr("href"))
                            .isEqualTo("/?page=0&source=MILAN&q=Suchspiel%20%26%20Erweiterung");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankSearchShowsAllOffersInSelectedSource(String query) throws Exception {
        saveOffer("Ein Spiel", OfferSource.MILAN, 1);
        saveOffer("Noch ein Spiel", OfferSource.MILAN, 2);
        saveOffer("Andere Quelle", OfferSource.UNKNOWNS, 3);

        mockMvc.perform(get("/").param("q", query).param("source", "MILAN"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".offer-card h2").eachText())
                            .containsExactly("Noch ein Spiel", "Ein Spiel");
                    assertThat(document.selectFirst("#title-search").val()).isEmpty();
                    assertThat(document.select(".search-panel p, .search-panel a")).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_"})
    void treatsSqlWildcardsAsLiteralTitleCharacters(String query) throws Exception {
        saveOffer("Angebot " + query + " Sonderedition", OfferSource.MILAN, 1);
        saveOffer("Anderes Spiel", OfferSource.MILAN, 2);

        mockMvc.perform(get("/").param("q", query))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(Jsoup.parse(result.getResponse().getContentAsString())
                        .select(".offer-card h2").eachText()).containsExactly("Angebot " + query + " Sonderedition"));
    }

    @Test
    void explainsMissingResultsAndEscapesSearchText() throws Exception {
        var query = "<script>alert('Titel')</script>";

        mockMvc.perform(get("/").param("q", query))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.selectFirst(".empty-state h2").text())
                            .isEqualTo("Keine passenden Angebote gefunden");
                    assertThat(document.selectFirst(".search-panel p").text()).contains("0 Treffer", query);
                    assertThat(document.selectFirst("#title-search").val()).isEqualTo(query);
                    assertThat(document.select(".search-panel script, .offer-card")).isEmpty();
                });
    }

    private void saveOffer(String title, OfferSource source, int index) {
        repository.saveAndFlush(Offer.create(source, OfferType.STANDARD, title,
                "https://shop.example/search/" + index, Instant.parse("2026-10-06T10:00:00Z").plusSeconds(index)));
    }
}
