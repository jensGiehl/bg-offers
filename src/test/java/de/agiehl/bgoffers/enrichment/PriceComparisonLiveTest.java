package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.PriceComparisonDocumentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_LIVE_PRICE_COMPARISON_TEST", matches = "(?i)true")
class PriceComparisonLiveTest {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36";

    @Test
    void findsCurrentScythePriceOnBrettspielAngebote() {
        var properties = liveProperties();
        var service = new PriceComparisonService(
                new PriceComparisonDocumentClient(properties),
                properties,
                new GameNameNormalizer(),
                new ObjectMapper());

        var result = service.lookup("Scythe", 169786);

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.url()).contains("/spiele/scythe/");
        assertThat(result.availablePrice()).isPositive();
    }

    private OfferProperties liveProperties() {
        var defaults = TestProperties.create();
        return new OfferProperties(
                defaults.sources(),
                new OfferProperties.Http(
                        Duration.ofSeconds(30),
                        USER_AGENT,
                        1,
                        Duration.ZERO,
                        Duration.ZERO,
                        1),
                defaults.schedule(),
                defaults.sourceHealth(),
                defaults.initialImport(),
                defaults.commitId(),
                defaults.telegram(),
                defaults.bgg());
    }
}
