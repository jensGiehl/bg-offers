package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.enrichment.PriceComparisonService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSchedulerTest {

    @Test
    void reportsFailedDailyExternalServiceChecksAndVerifiesScraperActivity() {
        var imports = mock(OfferImportService.class);
        var comparison = mock(PriceComparisonService.class);
        var bgg = mock(BggLookupService.class);
        var scraperHealth = mock(ScraperHealthService.class);
        var notifier = mock(OfferNotifier.class);
        var messages = ArgumentCaptor.forClass(String.class);
        when(comparison.healthCheck()).thenReturn(false);
        when(bgg.healthCheck()).thenReturn(false);
        var scheduler = new OfferScheduler(imports, comparison, bgg, scraperHealth, notifier);

        scheduler.verifyHealth();

        verify(notifier, org.mockito.Mockito.times(2)).sendHealthAlert(messages.capture());
        verify(scraperHealth).verifySources();
        assertThat(messages.getAllValues())
                .anyMatch(message -> message.contains("Scythe") && message.contains("brettspiel-angebote.de"))
                .anyMatch(message -> message.contains("Magical Athlete") && message.contains("BoardGameGeek"));
    }
}
