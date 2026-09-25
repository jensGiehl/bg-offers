package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OfferScraperConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(SessionDocumentClient.class, () -> mock(SessionDocumentClient.class))
            .withBean(OfferProperties.class, TestProperties::create)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(
                    BggMarketScraper.class,
                    MilanScraper.class,
                    SpieleOffensiveScraper.class,
                    UnknownsScraper.class);

    @Test
    void enablesAllScrapersByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(MilanScraper.class)
                .hasSingleBean(SpieleOffensiveScraper.class)
                .hasSingleBean(BggMarketScraper.class)
                .hasSingleBean(UnknownsScraper.class));
    }

    @Test
    void disablesMilanScraperIndependently() {
        contextRunner
                .withPropertyValues("offers.sources.milan-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MilanScraper.class);
                    assertThat(context).hasSingleBean(SpieleOffensiveScraper.class);
                    assertThat(context).hasSingleBean(BggMarketScraper.class);
                    assertThat(context).hasSingleBean(UnknownsScraper.class);
                });
    }

    @Test
    void disablesSpieleOffensiveScraperIndependently() {
        contextRunner
                .withPropertyValues("offers.sources.spiele-offensive-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MilanScraper.class);
                    assertThat(context).doesNotHaveBean(SpieleOffensiveScraper.class);
                    assertThat(context).hasSingleBean(BggMarketScraper.class);
                    assertThat(context).hasSingleBean(UnknownsScraper.class);
                });
    }

    @Test
    void disablesUnknownsScraperIndependently() {
        contextRunner
                .withPropertyValues("offers.sources.unknowns-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MilanScraper.class);
                    assertThat(context).hasSingleBean(SpieleOffensiveScraper.class);
                    assertThat(context).hasSingleBean(BggMarketScraper.class);
                    assertThat(context).doesNotHaveBean(UnknownsScraper.class);
                });
    }

    @Test
    void disablesBggMarketScraperIndependently() {
        contextRunner
                .withPropertyValues("offers.sources.bgg-market-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MilanScraper.class);
                    assertThat(context).hasSingleBean(SpieleOffensiveScraper.class);
                    assertThat(context).doesNotHaveBean(BggMarketScraper.class);
                    assertThat(context).hasSingleBean(UnknownsScraper.class);
                });
    }
}
