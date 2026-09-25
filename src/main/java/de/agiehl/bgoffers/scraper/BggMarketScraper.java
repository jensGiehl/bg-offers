package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "offers.sources",
        name = "bgg-market-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BggMarketScraper implements OfferScraper {

    private static final Logger LOGGER = LoggerFactory.getLogger(BggMarketScraper.class);
    private static final URI BGG_BASE_URI = URI.create("https://boardgamegeek.com/");

    private final DocumentClient client;
    private final OfferProperties properties;
    private final ObjectMapper objectMapper;

    public BggMarketScraper(
            DocumentClient client,
            OfferProperties properties,
            ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public OfferSource source() {
        return OfferSource.BGG_MARKET;
    }

    @Override
    public List<ScrapedOffer> scrape() {
        var root = objectMapper.readTree(client.fetchJson(properties.sources().bggMarket()));
        var products = root.path("products");
        if (!products.isArray()) {
            throw new SourceAccessException("Der BGG Market lieferte keine Produktliste");
        }
        var offers = new LinkedHashMap<String, ScrapedOffer>();
        for (var product : products) {
            parseProduct(product).ifPresent(offer -> offers.putIfAbsent(offer.sourceOfferId(), offer));
        }
        return List.copyOf(offers.values());
    }

    Optional<ScrapedOffer> parseProduct(JsonNode product) {
        var productId = text(product, "productid");
        var objectId = integer(product, "objectid");
        var name = text(product.path("version"), "name");
        if (name == null) {
            name = text(product.path("objectlink"), "name");
        }
        var productHref = text(product, "producthref");
        var price = decimal(product, "price");
        if (productId == null || name == null || productHref == null || price == null) {
            LOGGER.warn("Unvollständiges BGG-Market-Angebot wird übersprungen: productid={}", productId);
            return Optional.empty();
        }
        return Optional.of(new ScrapedOffer(
                source(),
                OfferType.STANDARD,
                name,
                BGG_BASE_URI.resolve(productHref).toString(),
                imageUrl(product),
                price,
                null,
                null,
                null,
                productId,
                objectId));
    }

    private String imageUrl(JsonNode product) {
        for (var path : List.of(
                product.path("version").path("imageSets").path("mediacard").path("src"),
                product.path("version").path("imageSets").path("square100").path("src"),
                product.path("objectlink").path("image").path("images").path("medium").path("url"))) {
            var value = path.stringValue("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        var value = node.path(field).stringValue("").trim();
        return value.isBlank() ? null : value;
    }

    private Integer integer(JsonNode node, String field) {
        try {
            var value = text(node, field);
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        try {
            var value = text(node, field);
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
