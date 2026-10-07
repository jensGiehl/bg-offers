package de.agiehl.bgoffers.pricecomparison;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_LIVE_PRICE_COMPARISON_TEST", matches = "(?i)true")
class PriceComparisonLiveTest {

    @Test
    void findsScythePriceThroughThePriceService() {
        var properties = liveProperties();
        var service = new PriceComparisonService(
                new PriceComparisonHttpClient(properties, Duration.ofSeconds(60)),
                new GameNameNormalizer());

        var result = service.lookup("Scythe", 169786);

        assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
        assertThat(result.url()).contains("/spiele/scythe/");
        assertThat(result.availablePrice()).isPositive();
    }

    private OfferProperties liveProperties() {
        var defaults = TestProperties.create();
        var sources = defaults.sources();
        return new OfferProperties(
                new OfferProperties.Sources(sources.spieleOffensive(), sources.milan(), sources.unknowns(),
                        sources.unknownsLogin(), sources.unknownsUsername(), sources.unknownsPassword(),
                        sources.bggMarket(), URI.create(System.getenv().getOrDefault(
                                "PRICE_COMPARISON_URL", "http://localhost:8077"))),
                new OfferProperties.Http(
                        Duration.ofSeconds(30),
                        defaults.http().userAgent(),
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
