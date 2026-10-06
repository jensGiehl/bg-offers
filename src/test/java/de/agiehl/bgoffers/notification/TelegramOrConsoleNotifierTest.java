package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.domain.WeeklyReport;
import de.agiehl.bgoffers.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramOrConsoleNotifierTest {

    @Test
    void sendsWeeklyReportAsHtmlWithLinkedNamesAndDisabledPreviews() throws Exception {
        var client = telegramClient(200, "{\"ok\":true}");
        var activityLog = mock(ActivityLogService.class);
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog,
                mock(TelegramOfferImage.class), client);
        var report = new WeeklyReport(Instant.parse("2026-10-04T16:00:00Z"),
                Instant.parse("2026-10-11T16:00:00Z"), 2, 1,
                List.of(new WeeklyReport.WithheldOffer("Testspiel", "https://shop.example/offer")));

        assertThat(notifier.sendWeeklyReport(report)).isTrue();

        var request = sentRequest(client);
        assertThat(request.uri().getPath()).endsWith("/sendMessage");
        assertThat(java.net.URLDecoder.decode(new String(requestBody(request), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8)).contains("parse_mode=HTML",
                "link_preview_options={\"is_disabled\":true}",
                "<a href=\"https://shop.example/offer\">Testspiel</a>");
        var detail = ArgumentCaptor.forClass(String.class);
        verify(activityLog).recordTelegramDelivery(org.mockito.ArgumentMatchers.eq(true), detail.capture());
        assertThat(detail.getValue()).contains("Wochenreport", "Testspiel (https://shop.example/offer)")
                .doesNotContain("<a", "<b>", "chat-id", "test-token");
    }

    @Test
    void reportsWeeklyDeliveryFailure() throws Exception {
        var client = telegramClient(400, "{\"ok\":false}");
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), mock(ActivityLogService.class),
                mock(TelegramOfferImage.class), client);
        var report = new WeeklyReport(Instant.parse("2026-10-04T16:00:00Z"),
                Instant.parse("2026-10-11T16:00:00Z"), 0, 0, List.of());

        assertThat(notifier.sendWeeklyReport(report)).isFalse();
    }

    @Test
    void omitsUnavailableEnrichmentAndAvailabilityFields() {
        var offer = offer();
        offer.setPrice(new BigDecimal("19.99"));
        offer.setAvailability("Keine Angabe");
        var notifier = new TelegramOrConsoleNotifier(
                TestProperties.create(), mock(ActivityLogService.class), mock(TelegramOfferImage.class));

        var html = notifier.htmlText(offer);
        var plainText = notifier.plainText(offer);

        assertThat(html)
                .contains("19,99 €", "Angebot")
                .doesNotContain("Preisvergleich", "Vergleich:", "Bestpreis", "BGG", "Kaufen", "Tausch", "📦");
        assertThat(plainText)
                .contains("Preis: 19,99 €", "URL: https://shop.example/offer")
                .doesNotContain("Verfügbarkeit", "Vergleich", "Bestpreis", "BGG", "Want to buy", "Want in trade");
    }

    @Test
    void rendersOnlyTheIndividualEnrichmentValuesThatExist() {
        var offer = offer();
        offer.setAvailability("sofort lieferbar");
        offer.setComparisonAvailablePrice(new BigDecimal("24.99"));
        offer.setBggRating(new BigDecimal("7.80"));
        var notifier = new TelegramOrConsoleNotifier(
                TestProperties.create(), mock(ActivityLogService.class), mock(TelegramOfferImage.class));

        var html = notifier.htmlText(offer);

        assertThat(html)
                .contains("sofort lieferbar", "Vergleich: 24,99 €", "BGG: 7.8")
                .doesNotContain("Bestpreis", "Kaufen", "Tausch");
    }

    @Test
    void rendersForumPostsWithOnlyTitleAndLink() {
        var offer = Offer.create(
                OfferSource.UNKNOWNS,
                OfferType.FORUM_POST,
                "Ein <gutes> Schnäppchen",
                "https://unknowns.de/forum/thread/42-ein-gutes-schnaeppchen/",
                Instant.parse("2026-09-25T10:00:00Z"));
        var notifier = new TelegramOrConsoleNotifier(
                TestProperties.create(), mock(ActivityLogService.class), mock(TelegramOfferImage.class));

        assertThat(notifier.htmlText(offer)).isEqualTo("""
                <b>Ein &lt;gutes&gt; Schnäppchen</b>
                <a href="https://unknowns.de/forum/thread/42-ein-gutes-schnaeppchen/">Beitrag öffnen</a>""");
        assertThat(notifier.plainText(offer)).isEqualTo(String.join(
                System.lineSeparator(),
                "Ein <gutes> Schnäppchen",
                "https://unknowns.de/forum/thread/42-ein-gutes-schnaeppchen/"));
    }

    @Test
    void uploadsTheRenderedPhotoWithUtf8CaptionAsMultipart() throws Exception {
        var offer = offer();
        offer.setName("Grüße & Würfel");
        offer.setImageUrl("https://shop.example/image.png");
        var renderer = mock(TelegramOfferImage.class);
        var photo = new byte[]{(byte) 137, 80, 78, 71, 0, (byte) 255};
        when(renderer.render(offer)).thenReturn(photo);
        var activityLog = mock(ActivityLogService.class);
        var client = telegramClient(200, "{\"ok\":true}");
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog, renderer, client);

        assertThat(notifier.sendOffer(offer)).isTrue();

        var request = sentRequest(client);
        assertThat(request.uri().getPath()).endsWith("/sendPhoto");
        var contentType = request.headers().firstValue("Content-Type").orElseThrow();
        assertThat(contentType).startsWith("multipart/form-data; boundary=");
        var body = requestBody(request);
        assertThat(body).containsSubsequence(photo);
        var text = new String(body, StandardCharsets.UTF_8);
        assertThat(text).contains("name=\"chat_id\"\r\n\r\nchat-id", "name=\"caption\"",
                "Grüße &amp; Würfel", "name=\"parse_mode\"\r\n\r\nHTML",
                "name=\"photo\"; filename=\"offer.png\"\r\nContent-Type: image/png\r\n\r\n");
        assertThat(text).endsWith("\r\n--" + contentType.substring(contentType.indexOf("boundary=") + 9) + "--\r\n");
        assertThat(text).doesNotContain(offer.getImageUrl());
        verify(activityLog).recordTelegramDelivery(true, """
                Bildnachricht
                🎲 Grüße & Würfel
                🔗 Angebot (https://shop.example/offer)""");
    }

    @Test
    void uploadsSpieleschmiedePhotosWithoutCaption() throws Exception {
        var offer = Offer.create(OfferSource.SPIELE_OFFENSIVE, OfferType.SPIELESCHMIEDE,
                "Projekt", "https://shop.example/project", Instant.now());
        offer.setImageUrl("https://shop.example/project.png");
        var renderer = mock(TelegramOfferImage.class);
        when(renderer.render(offer)).thenReturn(new byte[]{1, 2, 3});
        var client = telegramClient(200, "{\"ok\":true}");
        var activityLog = mock(ActivityLogService.class);
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog, renderer, client);

        assertThat(notifier.sendOffer(offer)).isTrue();

        var request = sentRequest(client);
        assertThat(request.uri().getPath()).endsWith("/sendPhoto");
        assertThat(new String(requestBody(request), StandardCharsets.UTF_8))
                .contains("name=\"photo\"").doesNotContain("name=\"caption\"", "name=\"parse_mode\"");
        verify(renderer).render(offer);
        verify(activityLog).recordTelegramDelivery(true, "Bildnachricht\n" + notifier.plainText(offer));
    }

    @Test
    void sendsUnknownsPostsWithALogoEvenWhenTheStoredImageIsMissing() throws Exception {
        var offer = Offer.create(OfferSource.UNKNOWNS, OfferType.FORUM_POST,
                "Forum-Deal", "https://unknowns.de/forum/thread/42-deal/", Instant.now());
        var renderer = mock(TelegramOfferImage.class);
        when(renderer.render(offer)).thenReturn(new byte[]{1, 2, 3});
        var client = telegramClient(200, "{\"ok\":true}");
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), mock(ActivityLogService.class), renderer, client);

        assertThat(notifier.sendOffer(offer)).isTrue();

        var request = sentRequest(client);
        assertThat(request.uri().getPath()).endsWith("/sendPhoto");
        assertThat(new String(requestBody(request), StandardCharsets.UTF_8))
                .contains("name=\"caption\"", "<b>Forum-Deal</b>", "Beitrag öffnen", offer.getSourceUrl());
        verify(renderer).render(offer);
    }

    @Test
    void sendsOffersWithoutImagesAsText() throws Exception {
        var renderer = mock(TelegramOfferImage.class);
        var client = telegramClient(200, "{\"ok\":true}");
        var activityLog = mock(ActivityLogService.class);
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog, renderer, client);

        assertThat(notifier.sendOffer(offer())).isTrue();

        assertThat(sentRequest(client).uri().getPath()).endsWith("/sendMessage");
        verifyNoInteractions(renderer);
        verify(activityLog).recordTelegramDelivery(true, """
                🎲 Testspiel
                🔗 Angebot (https://shop.example/offer)""");
    }

    @Test
    void recordsTheSystemCheckContentWithLineBreaksAndDecodedHtml() throws Exception {
        var client = telegramClient(200, "{\"ok\":true}");
        var activityLog = mock(ActivityLogService.class);
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog,
                mock(TelegramOfferImage.class), client);

        assertThat(notifier.sendSystemCheck(true, "Commit: 0123456\nMilan-Spiele: ✅\nTest <Quelle> & Preis")).isTrue();

        verify(activityLog).recordTelegramDelivery(true, """
                ✅ Systemcheck erfolgreich
                Commit: 0123456
                Milan-Spiele: ✅
                Test <Quelle> & Preis""");
    }

    @Test
    void leavesFailedImagePreparationUnsentForRetry() throws Exception {
        var offer = offer();
        offer.setImageUrl("https://shop.example/broken.png");
        var renderer = mock(TelegramOfferImage.class);
        when(renderer.render(offer)).thenThrow(new IOException("HTTP 503"));
        var activityLog = mock(ActivityLogService.class);
        var client = mock(HttpClient.class);
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog, renderer, client);

        assertThat(notifier.sendOffer(offer)).isFalse();

        verify(activityLog).recordTelegramDelivery(org.mockito.ArgumentMatchers.eq(false), any(String.class));
        verifyNoInteractions(client);
    }

    @Test
    void recordsRejectedPhotoUploadsAsFailed() throws Exception {
        var offer = offer();
        offer.setImageUrl("https://shop.example/image.png");
        var renderer = mock(TelegramOfferImage.class);
        when(renderer.render(offer)).thenReturn(new byte[]{1, 2, 3});
        var activityLog = mock(ActivityLogService.class);
        var client = telegramClient(400, "{\"ok\":false}");
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog, renderer, client);

        assertThat(notifier.sendOffer(offer)).isFalse();

        verify(activityLog).recordTelegramDelivery(org.mockito.ArgumentMatchers.eq(false), any(String.class));
    }

    @Test
    void skipsImagePreparationWhenTelegramIsNotConfigured() {
        var offer = offer();
        offer.setImageUrl("https://shop.example/image.png");
        var renderer = mock(TelegramOfferImage.class);
        var notifier = new TelegramOrConsoleNotifier(TestProperties.create(), mock(ActivityLogService.class), renderer);

        assertThat(notifier.sendOffer(offer)).isTrue();

        verifyNoInteractions(renderer);
    }

    @Test
    void recordsHttpFailureDetailsWithoutTelegramCredentials() throws Exception {
        var client = telegramClient(403,
                "{\"ok\":false,\"description\":\"Forbidden: test-token chat-id blocked\"}");
        var activityLog = mock(ActivityLogService.class);
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog,
                mock(TelegramOfferImage.class), client);

        assertThat(notifier.sendOffer(offer())).isFalse();

        var detail = ArgumentCaptor.forClass(String.class);
        verify(activityLog).recordTelegramDelivery(org.mockito.ArgumentMatchers.eq(false), detail.capture());
        assertThat(detail.getValue()).contains("sendMessage", "HTTP 403", "Forbidden", "blocked")
                .doesNotContain("test-token", "chat-id");
    }

    @Test
    void recordsNetworkFailureDetails() throws Exception {
        var client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection reset"));
        var activityLog = mock(ActivityLogService.class);
        var notifier = new TelegramOrConsoleNotifier(telegramProperties(), activityLog,
                mock(TelegramOfferImage.class), client);

        assertThat(notifier.sendOffer(offer())).isFalse();

        verify(activityLog).recordTelegramDelivery(false, "sendMessage: Connection reset");
    }

    private OfferProperties telegramProperties() {
        var properties = TestProperties.create();
        return new OfferProperties(properties.sources(), properties.http(), properties.schedule(),
                properties.sourceHealth(), properties.initialImport(), properties.commitId(),
                new OfferProperties.Telegram("test-token", "chat-id"), properties.bgg());
    }

    private HttpClient telegramClient(int statusCode, String body) throws Exception {
        var client = mock(HttpClient.class);
        HttpResponse<String> response = mock();
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return client;
    }

    private HttpRequest sentRequest(HttpClient client) throws Exception {
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        return request.getValue();
    }

    private byte[] requestBody(HttpRequest request) throws Exception {
        var output = new ByteArrayOutputStream();
        var result = new CompletableFuture<byte[]>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer buffer) {
                var bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(output.toByteArray());
            }
        });
        return result.get(5, TimeUnit.SECONDS);
    }

    private Offer offer() {
        return Offer.create(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Testspiel",
                "https://shop.example/offer",
                Instant.parse("2026-09-25T10:00:00Z"));
    }
}
