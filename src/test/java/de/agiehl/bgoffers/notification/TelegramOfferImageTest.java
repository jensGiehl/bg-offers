package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramOfferImageTest {

    private final HttpClient client = mock(HttpClient.class);
    private final TelegramOfferImage renderer = new TelegramOfferImage(TestProperties.create(), client);

    @ParameterizedTest
    @CsvSource({"SPIELE_OFFENSIVE,SO", "MILAN,Milan", "BGG_MARKET,BGG"})
    void addsTheSourceBadgeAndPreservesTheProductImage(OfferSource source, String label) throws Exception {
        respond(png(600, 400), "image/png", 200);

        var photo = ImageIO.read(new ByteArrayInputStream(renderer.render(offer(source))));

        assertThat(source.getBadgeLabel()).isEqualTo(label);
        assertThat(photo.getWidth()).isEqualTo(600);
        assertThat(photo.getHeight()).isEqualTo(400);
        assertThat(photo.getRGB(300, 200)).isEqualTo(Color.RED.getRGB());
        assertThat(photo.getRGB(580, 25)).isEqualTo(new Color(24, 49, 83).getRGB());
        var textPixels = 0;
        for (var y = 10; y < 50; y++) {
            for (var x = 450; x < 590; x++) {
                if (photo.getRGB(x, y) == Color.WHITE.getRGB()) {
                    textPixels++;
                }
            }
        }
        assertThat(textPixels).isPositive();
    }

    @Test
    void usesAndRasterizesTheUnknownsLogoEvenForExistingOffersWithAnotherImage() throws Exception {
        var svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="180" height="38" viewBox="0 0 180 38">
                  <rect width="180" height="38" fill="red"/>
                </svg>
                """;
        respond(svg.getBytes(StandardCharsets.UTF_8), "image/svg+xml", 200);

        var photo = ImageIO.read(new ByteArrayInputStream(renderer.render(offer(OfferSource.UNKNOWNS))));

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo(OfferSource.UNKNOWNS_LOGO_URL);
        assertThat(photo.getWidth()).isEqualTo(1200);
        assertThat(photo.getHeight()).isBetween(250, 255);
        assertThat(photo.getRGB(600, 100)).isEqualTo(Color.RED.getRGB());
        assertThat(photo.getRGB(1180, 25)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void scalesLargeImagesToTelegramDimensions() throws Exception {
        respond(png(2400, 1600), "image/png", 200);

        var photo = ImageIO.read(new ByteArrayInputStream(renderer.render(offer(OfferSource.MILAN))));

        assertThat(photo.getWidth()).isEqualTo(1200);
        assertThat(photo.getHeight()).isEqualTo(800);
    }

    @Test
    void removesEmptyInkscapeFlowTextAndKeepsWhiteLogoDetailsVisible() throws Exception {
        var svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="180" height="38" viewBox="0 0 180 38">
                  <flowRoot><flowRegion><rect width="100" height="20"/></flowRegion><flowPara/></flowRoot>
                  <path d="M 80,10 H 100 V 30 H 80 Z" fill="white"/>
                </svg>
                """;
        respond(svg.getBytes(StandardCharsets.UTF_8), "image/svg+xml", 200);

        var photo = ImageIO.read(new ByteArrayInputStream(renderer.render(offer(OfferSource.UNKNOWNS))));

        assertThat(photo.getRGB(0, 0)).isEqualTo(new Color(65, 121, 173).getRGB());
        assertThat(photo.getRGB(600, 100)).isEqualTo(Color.WHITE.getRGB());
    }

    @Test
    void padsSmallImagesSoTheBadgeRemainsReadable() throws Exception {
        respond(png(20, 10), "image/png", 200);

        var photo = ImageIO.read(new ByteArrayInputStream(renderer.render(offer(OfferSource.MILAN))));

        assertThat(photo.getWidth()).isEqualTo(320);
        assertThat(photo.getHeight()).isEqualTo(180);
        assertThat(photo.getRGB(160, 90)).isEqualTo(Color.RED.getRGB());
        assertThat(photo.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
    }

    @Test
    void rejectsFailedDownloads() throws Exception {
        respond(new byte[0], "image/png", 503);

        assertThatThrownBy(() -> renderer.render(offer(OfferSource.MILAN)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void rejectsUnsupportedImageData() throws Exception {
        respond("not an image".getBytes(StandardCharsets.UTF_8), "text/html", 200);

        assertThatThrownBy(() -> renderer.render(offer(OfferSource.MILAN)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Nicht unterstütztes Angebotsbild");
    }

    private Offer offer(OfferSource source) {
        var offer = Offer.create(source, OfferType.STANDARD, "Testspiel", "https://shop.example/offer", Instant.now());
        offer.setImageUrl("https://shop.example/product.png");
        return offer;
    }

    private byte[] png(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void respond(byte[] body, String contentType, int statusCode) throws Exception {
        HttpResponse<byte[]> response = mock();
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (_, _) -> true));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
}
