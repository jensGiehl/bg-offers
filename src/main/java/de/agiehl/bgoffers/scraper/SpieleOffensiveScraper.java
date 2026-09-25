package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(
        prefix = "offers.sources",
        name = "spiele-offensive-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SpieleOffensiveScraper implements OfferScraper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpieleOffensiveScraper.class);
    private static final Pattern CURRENT_PRICE = Pattern.compile("(?i)jetzt\\s+nur\\s*([\\d.,]+)\\s*€");
    private static final Pattern BANNER_DATA = Pattern.compile(",\\s*`([^`]*)`\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\)\\s*;?");
    private static final Pattern QUANTITY = Pattern.compile("(?i)Noch\\s+verfügbar\\s+(\\d+)\\s+von\\s+(\\d+)");
    private static final Pattern GROUP_DEAL_PRICE_FIELD = Pattern.compile("uebergabe\\[\\d+\\]\\[3\\]");

    private final DocumentClient client;
    private final OfferProperties properties;

    public SpieleOffensiveScraper(DocumentClient client, OfferProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public OfferSource source() {
        return OfferSource.SPIELE_OFFENSIVE;
    }

    @Override
    public List<ScrapedOffer> scrape() {
        var landingPage = client.fetch(properties.sources().spieleOffensive());
        return parseLandingPage(landingPage);
    }

    List<ScrapedOffer> parseLandingPage(Document landingPage) {
        var bannersByUrl = new LinkedHashMap<String, Element>();
        for (var anchor : landingPage.select("#wrapper_startseite a[href]")) {
            var url = anchor.absUrl("href");
            if (isRelevant(url)) {
                bannersByUrl.putIfAbsent(url, anchor);
            }
        }

        var offers = new ArrayList<ScrapedOffer>();
        bannersByUrl.forEach((url, banner) -> parseBanner(url, banner).ifPresent(offers::add));
        return List.copyOf(offers);
    }

    private boolean isRelevant(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        var lower = url.toLowerCase(Locale.ROOT);
        return !lower.contains("inspirations-tv")
                && (lower.contains("/spiel/")
                || lower.contains("cmd=gruppendeal")
                || lower.contains("/spieleschmiede/"));
    }

    private Optional<ScrapedOffer> parseBanner(String url, Element banner) {
        var imageUrl = bannerImageUrl(banner);
        if (url.toLowerCase(Locale.ROOT).contains("/spieleschmiede/")) {
            return Optional.of(new ScrapedOffer(
                    source(),
                    OfferType.SPIELESCHMIEDE,
                    spieleschmiedeName(banner, url),
                    url,
                    imageUrl,
                    null,
                    "Crowdfunding-Projekt",
                    null,
                    null));
        }

        try {
            var detailPage = client.fetch(URI.create(url));
            return url.toLowerCase(Locale.ROOT).contains("cmd=gruppendeal")
                    ? Optional.of(parseGroupDeal(detailPage, url, imageUrl))
                    : Optional.of(parseProduct(detailPage, url, imageUrl));
        } catch (SourceAccessException exception) {
            LOGGER.warn("Detailseite {} konnte nicht ausgewertet werden: {}", url, exception.getMessage());
            return fallbackProduct(url, banner, imageUrl);
        }
    }

    private ScrapedOffer parseProduct(Document document, String url, String bannerImageUrl) {
        var name = requiredText(document.selectFirst("h1"), "Spielname", url);
        var priceElement = document.selectFirst(".preis");
        var price = extractCurrentPrice(priceElement == null ? "" : priceElement.text())
                .orElseThrow(() -> new SourceAccessException("Kein aktueller Preis auf %s gefunden".formatted(url)));
        return new ScrapedOffer(
                source(),
                OfferType.STANDARD,
                name,
                url,
                bannerImageUrl,
                price,
                extractAvailability(document),
                null,
                null);
    }

    private ScrapedOffer parseGroupDeal(Document document, String url, String bannerImageUrl) {
        var name = requiredText(document.selectFirst("h1"), "Gruppendeal-Name", url);
        var price = extractGroupDealPrice(document)
                .orElseThrow(() -> new SourceAccessException("Kein Gruppendeal-Preis auf %s gefunden".formatted(url)));
        var quantityMatcher = QUANTITY.matcher(document.text());
        Integer available = null;
        Integer total = null;
        String availability = "Solange der Vorrat reicht";
        if (quantityMatcher.find()) {
            available = Integer.valueOf(quantityMatcher.group(1));
            total = Integer.valueOf(quantityMatcher.group(2));
            availability = "Noch verfügbar %d von %d".formatted(available, total);
        }
        return new ScrapedOffer(
                source(),
                OfferType.GROUP_DEAL,
                name,
                url,
                bannerImageUrl,
                price,
                availability,
                available,
                total);
    }

    private Optional<BigDecimal> extractGroupDealPrice(Document document) {
        var cartForm = document.selectFirst("#gdShoppingCartForm");
        if (cartForm == null) {
            return Optional.empty();
        }
        return cartForm.select("input[name][value]").stream()
                .filter(input -> GROUP_DEAL_PRICE_FIELD.matcher(input.attr("name")).matches())
                .map(input -> MoneyParser.parse(input.attr("value")))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<ScrapedOffer> fallbackProduct(String url, Element banner, String imageUrl) {
        if (url.toLowerCase(Locale.ROOT).contains("cmd=gruppendeal")) {
            return Optional.empty();
        }
        var matcher = BANNER_DATA.matcher(banner.attr("onclick"));
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new ScrapedOffer(
                source(),
                OfferType.STANDARD,
                matcher.group(1).trim(),
                url,
                imageUrl,
                new BigDecimal(matcher.group(2)),
                "Detailseite derzeit nicht abrufbar",
                null,
                null));
    }

    private Optional<BigDecimal> extractCurrentPrice(String text) {
        var matcher = CURRENT_PRICE.matcher(text);
        return matcher.find() ? MoneyParser.parse(matcher.group(1)) : Optional.empty();
    }

    private String bannerImageUrl(Element banner) {
        var images = banner.select("img[src]");
        var image = images.stream()
                .filter(candidate -> !isGeneratedOfferLayer(candidate))
                .findFirst()
                .orElse(images.first());
        return image == null ? null : image.absUrl("src");
    }

    private boolean isGeneratedOfferLayer(Element image) {
        return image.attr("src").toLowerCase(Locale.ROOT).contains("/gfx/bilder/display_angebot");
    }

    private String extractAvailability(Document document) {
        var buyBox = document.selectFirst(".sellw");
        if (buyBox == null || buyBox.parent() == null) {
            return "Keine Angabe";
        }
        var text = buyBox.parent().text();
        var marker = "versandkostenfrei in Deutschland";
        var markerIndex = text.indexOf(marker);
        if (markerIndex >= 0) {
            text = text.substring(markerIndex + marker.length()).trim();
        }
        var endIndex = text.indexOf(" oder ");
        if (endIndex >= 0) {
            text = text.substring(0, endIndex).trim();
        }
        return text.isBlank() ? "Keine Angabe" : text;
    }

    private String spieleschmiedeName(Element banner, String url) {
        var image = banner.selectFirst("img[alt]");
        if (image != null && !image.attr("alt").isBlank()) {
            return image.attr("alt").replaceFirst("(?i)^Jetzt in der Spieleschmiede:\\s*", "").trim();
        }
        return URI.create(url).getPath().replaceFirst(".*/", "").replace('-', ' ');
    }

    private String requiredText(Element element, String field, String url) {
        if (element == null || element.text().isBlank()) {
            throw new SourceAccessException("%s auf %s nicht gefunden".formatted(field, url));
        }
        return element.text().trim();
    }
}
