package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.DocumentClient;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
public class PriceComparisonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonService.class);
    private static final Pattern BGG_ID_PATTERN = Pattern.compile("/boardgame/(\\d+)(?:[/#?]|$)");

    private final DocumentClient client;
    private final OfferProperties properties;
    private final GameNameNormalizer normalizer;

    public PriceComparisonService(
            @Qualifier("priceComparisonDocumentClient") DocumentClient client,
            OfferProperties properties,
            GameNameNormalizer normalizer) {
        this.client = client;
        this.properties = properties;
        this.normalizer = normalizer;
    }

    public PriceComparisonResult lookup(String gameName) {
        return lookup(gameName, null);
    }

    public PriceComparisonResult lookup(String gameName, Integer bggId) {
        if (bggId == null && normalizer.isBundle(gameName)) {
            return PriceComparisonResult.withStatus(LookupStatus.SKIPPED);
        }
        try {
            var searchTerm = normalizer.searchTerm(gameName);
            if (searchTerm.isBlank()) {
                return PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND);
            }
            var baseUri = properties.sources().priceComparison();
            var encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
            var searchUri = baseUri.resolve("suche/?s=" + encodedTerm);
            LOGGER.debug("Preisvergleich für {}: Suche mit Suchbegriff '{}'", gameName, searchTerm);
            var detailPage = client.fetchFollowingRedirect(searchUri);
            var detailBggId = bggId(detailPage);
            if (bggId != null && detailBggId != null && !bggId.equals(detailBggId)) {
                LOGGER.debug("Preisvergleich für {}: Detailseite gehört zur abweichenden BGG-ID {}",
                        gameName, detailBggId);
                return PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND);
            }
            return priceResult(detailPage);
        } catch (RuntimeException exception) {
            LOGGER.warn("Preisvergleich für {} ist fehlgeschlagen: {}", gameName, exception.getMessage());
            return PriceComparisonResult.withStatus(LookupStatus.ERROR);
        }
    }

    public boolean healthCheck() {
        var result = lookup("Scythe");
        return result.status() == LookupStatus.FOUND && result.availablePrice() != null;
    }

    private PriceComparisonResult priceResult(Document detailPage) {
        var lowPrice = decimalAttribute(detailPage.selectFirst("[itemprop=offers] meta[itemprop=lowPrice]"), "content");
        var bestPrice = decimalAttribute(detailPage.selectFirst("[data-absolute-bestprice]"), "data-absolute-bestprice");
        if (lowPrice == null) {
            return PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND);
        }
        return new PriceComparisonResult(LookupStatus.FOUND, detailPage.location(), lowPrice, bestPrice);
    }

    private BigDecimal decimalAttribute(Element element, String attribute) {
        if (element == null || element.attr(attribute).isBlank()) {
            return null;
        }
        return new BigDecimal(element.attr(attribute));
    }

    private Integer bggId(Document detailPage) {
        var bggLink = detailPage.selectFirst("a[href*='boardgamegeek.com/boardgame/']");
        if (bggLink == null) {
            return null;
        }
        var matcher = BGG_ID_PATTERN.matcher(bggLink.absUrl("href"));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
