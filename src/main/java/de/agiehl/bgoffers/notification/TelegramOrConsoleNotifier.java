package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.service.ActivityLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramOrConsoleNotifier implements OfferNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramOrConsoleNotifier.class);

    private final OfferProperties properties;
    private final HttpClient httpClient;
    private final ActivityLogService activityLogService;

    public TelegramOrConsoleNotifier(OfferProperties properties, ActivityLogService activityLogService) {
        this.properties = properties;
        this.activityLogService = activityLogService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public boolean sendOffer(Offer offer) {
        if (!properties.telegram().configured()) {
            LOGGER.info("ANGEBOTSMELDUNG\n{}", plainText(offer));
            return true;
        }
        if (offer.getType() == OfferType.SPIELESCHMIEDE && present(offer.getImageUrl())) {
            return send("sendPhoto", fields("chat_id", properties.telegram().chatId(), "photo", offer.getImageUrl()));
        }

        var message = htmlText(offer);
        if (present(offer.getImageUrl())) {
            return send("sendPhoto", fields(
                    "chat_id", properties.telegram().chatId(),
                    "photo", offer.getImageUrl(),
                    "caption", message,
                    "parse_mode", "HTML"));
        }
        return send("sendMessage", fields(
                "chat_id", properties.telegram().chatId(),
                "text", message,
                "parse_mode", "HTML",
                "disable_web_page_preview", "true"));
    }

    @Override
    public boolean sendHealthAlert(String message) {
        if (!properties.telegram().configured()) {
            LOGGER.error("QUELLENWARNUNG: {}", message);
            return true;
        }
        return send("sendMessage", fields(
                "chat_id", properties.telegram().chatId(),
                "text", "⚠️ <b>Quellenwarnung</b>\n" + escapeHtml(message),
                "parse_mode", "HTML"));
    }

    String htmlText(Offer offer) {
        if (offer.getType() == OfferType.FORUM_POST) {
            return "<b>%s</b>\n%s".formatted(
                    escapeHtml(shorten(offer.getName(), 180)),
                    link(offer.getSourceUrl(), "Beitrag öffnen"));
        }
        var lines = new ArrayList<String>();
        lines.add("🎲 <b>" + escapeHtml(shorten(offer.getName(), 180)) + "</b>");
        if (offer.getPrice() != null) {
            lines.add("💶 " + money(offer.getPrice()));
        }
        if (meaningfulAvailability(offer.getAvailability())) {
            lines.add("📦 " + escapeHtml(shorten(offer.getAvailability(), 180)));
        }

        var comparison = new ArrayList<String>();
        if (offer.getComparisonAvailablePrice() != null) {
            comparison.add("Vergleich: " + money(offer.getComparisonAvailablePrice()));
        }
        if (offer.getComparisonBestPrice() != null) {
            comparison.add("Bestpreis: " + money(offer.getComparisonBestPrice()));
        }
        if (!comparison.isEmpty()) {
            lines.add("🔎 " + String.join(" · ", comparison));
        }

        var bgg = new ArrayList<String>();
        if (offer.getBggRating() != null) {
            bgg.add("BGG: " + decimal(offer.getBggRating()));
        }
        if (offer.getBggWantToBuy() != null) {
            bgg.add("Kaufen: " + number(offer.getBggWantToBuy()));
        }
        if (offer.getBggWantInTrade() != null) {
            bgg.add("Tausch: " + number(offer.getBggWantInTrade()));
        }
        if (!bgg.isEmpty()) {
            lines.add("⭐ " + String.join(" · ", bgg));
        }

        var links = new ArrayList<String>();
        links.add(link(offer.getSourceUrl(), "Angebot"));
        if (present(offer.getComparisonUrl())) {
            links.add(link(offer.getComparisonUrl(), "Preisvergleich"));
        }
        if (offer.getBggId() != null) {
            links.add(link("https://boardgamegeek.com/boardgame/" + offer.getBggId(), "BGG"));
        }
        lines.add("🔗 " + String.join(" · ", links));
        return String.join("\n", lines);
    }

    String plainText(Offer offer) {
        if (offer.getType() == OfferType.FORUM_POST) {
            return offer.getName() + System.lineSeparator() + offer.getSourceUrl();
        }
        if (offer.getType() == OfferType.SPIELESCHMIEDE) {
            var lines = new ArrayList<String>();
            lines.add("Spieleschmiede: " + offer.getName());
            if (present(offer.getImageUrl())) {
                lines.add("Bild: " + offer.getImageUrl());
            }
            lines.add("URL: " + offer.getSourceUrl());
            return String.join(System.lineSeparator(), lines);
        }
        var lines = new ArrayList<String>();
        lines.add(offer.getName());
        if (offer.getPrice() != null) {
            lines.add("Preis: " + money(offer.getPrice()));
        }
        if (meaningfulAvailability(offer.getAvailability())) {
            lines.add("Verfügbarkeit: " + offer.getAvailability());
        }
        if (offer.getComparisonAvailablePrice() != null) {
            lines.add("Vergleich: " + money(offer.getComparisonAvailablePrice()));
        }
        if (offer.getComparisonBestPrice() != null) {
            lines.add("Bestpreis: " + money(offer.getComparisonBestPrice()));
        }
        if (offer.getBggRating() != null) {
            lines.add("BGG: " + decimal(offer.getBggRating()));
        }
        if (offer.getBggWantToBuy() != null) {
            lines.add("Want to buy: " + number(offer.getBggWantToBuy()));
        }
        if (offer.getBggWantInTrade() != null) {
            lines.add("Want in trade: " + number(offer.getBggWantInTrade()));
        }
        lines.add("URL: " + offer.getSourceUrl());
        return String.join(System.lineSeparator(), lines);
    }

    private boolean send(String method, List<Field> fields) {
        var endpoint = URI.create("https://api.telegram.org/bot" + properties.telegram().botToken() + "/" + method);
        var body = fields.stream()
                .map(field -> encode(field.name()) + "=" + encode(field.value()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var successful = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && response.body().contains("\"ok\":true");
            if (!successful) {
                LOGGER.error("Telegram-Aufruf {} ist mit HTTP {} fehlgeschlagen", method, response.statusCode());
            }
            activityLogService.recordTelegramDelivery(successful);
            return successful;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Telegram-Aufruf {} wurde unterbrochen", method);
            activityLogService.recordTelegramDelivery(false);
            return false;
        } catch (IOException exception) {
            LOGGER.error("Telegram-Aufruf {} ist fehlgeschlagen: {}", method, exception.getMessage());
            activityLogService.recordTelegramDelivery(false);
            return false;
        }
    }

    private List<Field> fields(String... values) {
        var fields = new ArrayList<Field>(values.length / 2);
        for (var index = 0; index < values.length; index += 2) {
            fields.add(new Field(values[index], values[index + 1]));
        }
        return fields;
    }

    private String link(String url, String label) {
        return "<a href=\"" + escapeHtml(url) + "\">" + escapeHtml(label) + "</a>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "–";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String shorten(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 1) + "…";
    }

    private String money(BigDecimal value) {
        return value == null ? "–" : String.format(java.util.Locale.GERMANY, "%.2f €", value);
    }

    private String decimal(BigDecimal value) {
        return value == null ? "–" : value.stripTrailingZeros().toPlainString();
    }

    private String number(Integer value) {
        return value == null ? "–" : value.toString();
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private boolean meaningfulAvailability(String value) {
        return present(value)
                && !value.equalsIgnoreCase("Keine Angabe")
                && !value.equalsIgnoreCase("–")
                && !value.toLowerCase(java.util.Locale.ROOT).contains("derzeit nicht abrufbar");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Field(String name, String value) {
    }
}
