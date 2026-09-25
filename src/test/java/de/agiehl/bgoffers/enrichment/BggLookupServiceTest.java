package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BggLookupServiceTest {

    @Test
    void skipsBundlesEvenWhenBggIsNotConfigured() {
        var service = new BggLookupService(TestProperties.create(), new GameNameNormalizer());

        assertThat(service.lookup("Scythe Bundle (deutsch)").status()).isEqualTo(LookupStatus.SKIPPED);
    }

    @Test
    void preservesKnownBggIdWhenTheApiTokenIsNotConfigured() {
        var service = new BggLookupService(TestProperties.create(), new GameNameNormalizer());

        var result = service.lookupById(350458);

        assertThat(result.status()).isEqualTo(LookupStatus.NOT_CONFIGURED);
        assertThat(result.id()).isEqualTo(350458);
    }

    @Test
    void reportsAnUnhealthyApiWhenTheTokenIsNotConfigured() {
        var service = new BggLookupService(TestProperties.create(), new GameNameNormalizer());

        assertThat(service.healthCheck()).isFalse();
    }
}
