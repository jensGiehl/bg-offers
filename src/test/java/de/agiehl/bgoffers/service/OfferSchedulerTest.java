package de.agiehl.bgoffers.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OfferSchedulerTest {

    @Test
    void verifiesExternalServicesAndScraperActivity() {
        var imports = mock(OfferImportService.class);
        var externalHealth = mock(ExternalHealthCheckService.class);
        var scraperHealth = mock(ScraperHealthService.class);
        var scheduler = new OfferScheduler(imports, externalHealth, scraperHealth);

        scheduler.verifyHealth();

        verify(externalHealth).verify();
        verify(scraperHealth).verifySources();
    }
}
