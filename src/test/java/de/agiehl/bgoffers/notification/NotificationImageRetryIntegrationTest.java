package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.repository.OfferRepository;
import de.agiehl.bgoffers.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-image-retries;DB_CLOSE_DELAY=-1",
        "offers.schedule.initial-delay=24h",
        "offers.startup-system-check-enabled=false"
})
class NotificationImageRetryIntegrationTest {

    @Autowired
    private OfferRepository repository;

    @Test
    void reloadsImageFailuresAndKeepsTextFallbackAfterNotifierRestart() throws Exception {
        var offer = Offer.create(OfferSource.MILAN, OfferType.STANDARD, "Testspiel",
                "https://shop.example/image-retry", Instant.now());
        offer.setImageUrl("https://shop.example/broken.png");
        var id = repository.save(offer).getId();
        var renderer = mock(TelegramOfferImage.class);
        when(renderer.render(any())).thenThrow(new IOException("Nicht unterstütztes Angebotsbild"));
        var client = mock(HttpClient.class);
        HttpResponse<String> response = mock();
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("{\"ok\":false}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var defaults = TestProperties.create();
        var properties = new OfferProperties(defaults.sources(), defaults.http(), defaults.schedule(),
                defaults.sourceHealth(), defaults.initialImport(), defaults.commitId(),
                new OfferProperties.Telegram("test-token", "chat-id"), defaults.bgg());

        for (var attempt = 1; attempt <= 3; attempt++) {
            var reloaded = repository.findById(id).orElseThrow();
            var notifier = new TelegramOrConsoleNotifier(properties, mock(ActivityLogService.class), renderer, client);
            assertThat(notifier.sendOffer(reloaded)).isFalse();
            repository.save(reloaded);
            assertThat(repository.findById(id).orElseThrow().getNotificationImageFailures()).isEqualTo(attempt);
        }

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true}");
        var reloaded = repository.findById(id).orElseThrow();
        var restarted = new TelegramOrConsoleNotifier(properties, mock(ActivityLogService.class), renderer, client);
        assertThat(restarted.sendOffer(reloaded)).isTrue();
        repository.save(reloaded);

        assertThat(repository.findById(id).orElseThrow().getNotificationImageFailures()).isEqualTo(3);
        verify(renderer, times(3)).render(any());
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getAllValues()).allSatisfy(request ->
                assertThat(request.uri().getPath()).endsWith("/sendMessage"));
    }
}
