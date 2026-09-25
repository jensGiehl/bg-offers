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
}
