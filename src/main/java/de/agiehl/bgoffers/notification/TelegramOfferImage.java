package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class TelegramOfferImage {

    private static final int MAXIMUM_DIMENSION = 1200;
    private static final int MINIMUM_WIDTH = 320;
    private static final int MINIMUM_HEIGHT = 180;
    private static final Color BADGE_COLOR = new Color(24, 49, 83);
    private static final Color UNKNOWNS_BACKGROUND = new Color(65, 121, 173);

    private final OfferProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public TelegramOfferImage(OfferProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(properties.http().timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    TelegramOfferImage(OfferProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public byte[] render(Offer offer) throws IOException, InterruptedException {
        var unknowns = offer.getSource() == OfferSource.UNKNOWNS;
        var url = unknowns ? OfferSource.UNKNOWNS_LOGO_URL : offer.getImageUrl();
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(properties.http().timeout())
                .header("User-Agent", properties.http().userAgent())
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Angebotsbild konnte nicht geladen werden: HTTP " + response.statusCode());
        }
        var svg = unknowns || response.headers().firstValue("Content-Type")
                .orElse("").contains("image/svg+xml")
                || request.uri().getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".svg");
        var image = svg ? readSvg(response.body()) : ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null) {
            throw new IOException("Nicht unterstütztes Angebotsbild");
        }
        return compose(image, offer.getSource());
    }

    private BufferedImage readSvg(byte[] bytes) throws IOException {
        var document = Jsoup.parse(new ByteArrayInputStream(bytes), null, "", Parser.xmlParser());
        document.select("flowRoot").stream()
                .filter(element -> element.text().isBlank())
                .forEach(Element::remove);
        var transcoder = new BufferedImageTranscoder();
        transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) MAXIMUM_DIMENSION);
        transcoder.addTranscodingHint(ImageTranscoder.KEY_EXECUTE_ONLOAD, false);
        transcoder.addTranscodingHint(ImageTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, false);
        try {
            transcoder.transcode(new TranscoderInput(new StringReader(document.outerHtml())), null);
            return transcoder.image;
        } catch (TranscoderException exception) {
            throw new IOException("SVG-Angebotsbild konnte nicht verarbeitet werden", exception);
        }
    }

    private byte[] compose(BufferedImage image, OfferSource source) throws IOException {
        var scale = Math.min(1.0, (double) MAXIMUM_DIMENSION / Math.max(image.getWidth(), image.getHeight()));
        var width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        var height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        var canvas = new BufferedImage(Math.max(MINIMUM_WIDTH, width), Math.max(MINIMUM_HEIGHT, height),
                BufferedImage.TYPE_INT_RGB);
        var graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setColor(source == OfferSource.UNKNOWNS ? UNKNOWNS_BACKGROUND : Color.WHITE);
            graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            graphics.drawImage(image, (canvas.getWidth() - width) / 2, (canvas.getHeight() - height) / 2,
                    width, height, null);
            if (source.getBadgeLabel() != null) {
                drawBadge(graphics, canvas, source.getBadgeLabel());
            }
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", output);
        return output.toByteArray();
    }

    private void drawBadge(Graphics2D graphics, BufferedImage canvas, String label) {
        var fontSize = Math.clamp(canvas.getWidth() / 25, 20, 40);
        var padding = fontSize / 2;
        var margin = Math.max(10, canvas.getWidth() / 60);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        var metrics = graphics.getFontMetrics();
        var width = metrics.stringWidth(label) + padding * 2;
        var height = metrics.getHeight() + padding;
        var x = canvas.getWidth() - width - margin;
        graphics.setColor(new Color(0, 0, 0, 65));
        graphics.fillRoundRect(x + 2, margin + 3, width, height, padding, padding);
        graphics.setColor(BADGE_COLOR);
        graphics.fillRoundRect(x, margin, width, height, padding, padding);
        graphics.setColor(Color.WHITE);
        graphics.drawString(label, x + padding, margin + padding / 2 + metrics.getAscent());
    }

    private static final class BufferedImageTranscoder extends ImageTranscoder {

        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage image, TranscoderOutput output) {
            this.image = image;
        }
    }
}
