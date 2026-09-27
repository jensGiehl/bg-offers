package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.TestProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserDocumentClientTest {

    @Test
    void rejectsRequestsToOtherOriginsBeforeStartingABrowser() {
        try (var client = new BrowserDocumentClient(TestProperties.create())) {
            assertThatThrownBy(() -> client.fetch(URI.create("https://example.org/")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("konfigurierten Preisvergleich");
        }
    }
}
