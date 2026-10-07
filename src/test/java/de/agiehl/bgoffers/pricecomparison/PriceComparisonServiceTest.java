package de.agiehl.bgoffers.pricecomparison;

import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import de.agiehl.bgoffers.scraper.SourceAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PriceComparisonServiceTest {

    private final PriceComparisonHttpClient client = mock(PriceComparisonHttpClient.class);
    private final PriceComparisonService service = new PriceComparisonService(client, new GameNameNormalizer());

    @ParameterizedTest
    @ValueSource(strings = {
            "  Die Glasstraße (German first edition)  ",
            "Die (2026) Glasstraße (German (first) edition)"
    })
    void sendsNormalizedNameAndBggIdToTheService(String gameName) {
        var expected = new PriceComparisonResult(LookupStatus.FOUND,
                "https://www.brettspiel-angebote.de/spiele/die-glasstrasse/100/",
                new BigDecimal("29.99"), new BigDecimal("20.00"));
        when(client.lookup("Die Glasstraße", 143693)).thenReturn(expected);

        assertThat(service.lookup(gameName, 143693)).isEqualTo(expected);
        verify(client).lookup("Die Glasstraße", 143693);
    }

    @Test
    void skipsSearchWhenOnlyParenthesizedContentRemains() {
        assertThat(service.lookup(" (123) (German first edition) ").status()).isEqualTo(LookupStatus.NOT_FOUND);
        verifyNoInteractions(client);
    }

    @Test
    void returnsErrorWhenTheServiceCannotBeReached() {
        when(client.lookup("Scythe", null)).thenThrow(new SourceAccessException("nicht erreichbar"));

        assertThat(service.lookup("Scythe").status()).isEqualTo(LookupStatus.ERROR);
        assertThat(service.healthCheck()).isFalse();
    }

    @Test
    void skipsBundlesWithoutAccessingTheService() {
        assertThat(service.lookup("Scythe Bundle (deutsch)").status()).isEqualTo(LookupStatus.SKIPPED);
        verifyNoInteractions(client);
    }

    @Test
    void searchesBundlesWithKnownBggId() {
        when(client.lookup("Scythe", 169786)).thenReturn(PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND));

        assertThat(service.lookup("Scythe Bundle (deutsch)", 169786).status()).isEqualTo(LookupStatus.NOT_FOUND);
        verify(client).lookup("Scythe", 169786);
    }

    @Test
    void healthCheckRequiresAnAvailablePrice() {
        when(client.lookup("Scythe", null))
                .thenReturn(new PriceComparisonResult(LookupStatus.FOUND, "https://compare.example/scythe",
                        null, new BigDecimal("32.50")))
                .thenReturn(new PriceComparisonResult(LookupStatus.FOUND, "https://compare.example/scythe",
                        new BigDecimal("44.90"), new BigDecimal("32.50")));

        assertThat(service.healthCheck()).isFalse();
        assertThat(service.healthCheck()).isTrue();
    }
}
