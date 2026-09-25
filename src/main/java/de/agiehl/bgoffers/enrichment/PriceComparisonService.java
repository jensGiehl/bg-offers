package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.scraper.DocumentClient;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PriceComparisonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonService.class);
    private static final double MINIMUM_MATCH = 0.55;
    private static final int MAXIMUM_BGG_CANDIDATES = 5;
    private static final Pattern BGG_ID_PATTERN = Pattern.compile("/boardgame/(\\d+)(?:[/#?]|$)");

    private final DocumentClient client;
    private final OfferProperties properties;
    private final GameNameNormalizer normalizer;
    private final ObjectMapper objectMapper;

    public PriceComparisonService(
            DocumentClient client,
            OfferProperties properties,
            GameNameNormalizer normalizer,
            ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
    }

    public PriceComparisonResult lookup(String gameName) {
        return lookup(gameName, null);
    }

    public PriceComparisonResult lookup(String gameName, Integer bggId) {
        if (bggId == null && normalizer.isBundle(gameName)) {
            return PriceComparisonResult.withStatus(LookupStatus.SKIPPED);
        }
        try {
            var baseUri = properties.sources().priceComparison();
            client.fetch(baseUri);
            var candidates = findCandidates(baseUri, gameName);
            if (candidates.isEmpty()) {
                return PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND);
            }
            return loadMatchingPrice(candidates, bggId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Preisvergleich für {} ist fehlgeschlagen: {}", gameName, exception.getMessage());
            return PriceComparisonResult.withStatus(LookupStatus.ERROR);
        }
    }

    public boolean healthCheck() {
        var result = lookup("Scythe");
        return result.status() == LookupStatus.FOUND && result.availablePrice() != null;
    }

    private List<PriceCandidate> findCandidates(URI baseUri, String gameName) {
        for (var searchTerm : searchTerms(gameName)) {
            var encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
            var searchUri = baseUri.resolve("quicksearch/?q=" + encodedTerm + "&source=header");
            var root = objectMapper.readTree(client.fetchJson(searchUri));
            if (!root.isArray()) {
                throw new IllegalStateException("Die Preisvergleichssuche lieferte keine Ergebnisliste");
            }
            var candidates = new ArrayList<PriceCandidate>();
            for (var result : root) {
                var name = result.path("name").stringValue("").trim();
                var url = result.path("url").stringValue("").trim();
                if (name.isBlank() || url.isBlank()) {
                    continue;
                }
                var score = normalizer.similarity(gameName, name);
                if (score >= MINIMUM_MATCH && normalizer.hasCompatibleEdition(gameName, name)) {
                    candidates.add(new PriceCandidate(name, baseUri.resolve(url), score));
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.stream()
                        .sorted(Comparator.comparingDouble(PriceCandidate::score).reversed())
                        .toList();
            }
        }
        return List.of();
    }

    private List<String> searchTerms(String gameName) {
        var normalizedName = normalizer.searchTerm(gameName);
        if (normalizedName.isBlank()) {
            return List.of();
        }
        var words = normalizedName.split("\\s+");
        var terms = new ArrayList<String>();
        var minimumLength = Math.min(2, words.length);
        for (var length = words.length; length >= minimumLength; length--) {
            terms.add(String.join(" ", Arrays.copyOf(words, length)));
        }
        return List.copyOf(terms);
    }

    private PriceComparisonResult loadMatchingPrice(List<PriceCandidate> candidates, Integer expectedBggId) {
        var bestCandidate = candidates.getFirst();
        Document bestCandidatePage = null;
        if (expectedBggId != null) {
            for (var candidate : candidates.stream().limit(MAXIMUM_BGG_CANDIDATES).toList()) {
                var detailPage = client.fetch(candidate.uri());
                if (candidate.equals(bestCandidate)) {
                    bestCandidatePage = detailPage;
                }
                if (expectedBggId.equals(bggId(detailPage))) {
                    return priceResult(candidate, detailPage);
                }
            }
        }
        if (bestCandidatePage == null) {
            bestCandidatePage = client.fetch(bestCandidate.uri());
        }
        return priceResult(bestCandidate, bestCandidatePage);
    }

    private PriceComparisonResult priceResult(PriceCandidate candidate, Document detailPage) {
        var lowPrice = decimalAttribute(detailPage.selectFirst("[itemprop=offers] meta[itemprop=lowPrice]"), "content");
        var bestPrice = decimalAttribute(detailPage.selectFirst("[data-absolute-bestprice]"), "data-absolute-bestprice");
        if (lowPrice == null) {
            return PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND);
        }
        return new PriceComparisonResult(LookupStatus.FOUND, candidate.uri().toString(), lowPrice, bestPrice);
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

    private record PriceCandidate(String name, URI uri, double score) {
    }
}
