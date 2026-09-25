package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgg.BggClient;
import de.agiehl.bgg.model.common.DecimalValue;
import de.agiehl.bgg.model.common.IntValue;
import de.agiehl.bgg.model.common.ThingType;
import de.agiehl.bgg.model.search.SearchItem;
import de.agiehl.bgg.model.thing.Thing;
import de.agiehl.bgg.request.SearchRequest;
import de.agiehl.bgg.request.ThingRequest;
import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class BggLookupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BggLookupService.class);
    private static final double MINIMUM_MATCH = 0.55;

    private final OfferProperties properties;
    private final GameNameNormalizer normalizer;

    public BggLookupService(OfferProperties properties, GameNameNormalizer normalizer) {
        this.properties = properties;
        this.normalizer = normalizer;
    }

    public BggResult lookup(String gameName) {
        if (normalizer.isBundle(gameName)) {
            return BggResult.withStatus(LookupStatus.SKIPPED);
        }
        if (!properties.bgg().configured()) {
            return BggResult.withStatus(LookupStatus.NOT_CONFIGURED);
        }
        try {
            var client = BggClient.of(properties.bgg().apiToken());
            var searchTerm = normalizer.searchTerm(gameName);
            if (searchTerm.isBlank()) {
                return BggResult.withStatus(LookupStatus.NOT_FOUND);
            }
            var response = client.search().fetch(SearchRequest.builder()
                    .query(searchTerm)
                    .types(Set.of(ThingType.BOARDGAME, ThingType.BOARDGAME_EXPANSION))
                    .build());
            var match = bestMatch(gameName, response.getItems());
            if (match == null) {
                return BggResult.withStatus(LookupStatus.NOT_FOUND);
            }
            var thingResponse = client.things().fetch(ThingRequest.builder()
                    .id(match.getId())
                    .stats(true)
                    .build());
            var thing = firstThing(thingResponse.getItems());
            if (thing == null || thing.getStatistics() == null || thing.getStatistics().getRatings() == null) {
                return BggResult.withStatus(LookupStatus.NOT_FOUND);
            }
            var ratings = thing.getStatistics().getRatings();
            return new BggResult(
                    LookupStatus.FOUND,
                    thing.getId(),
                    decimalValue(ratings.getAverage()),
                    intValue(ratings.getWanting()),
                    intValue(ratings.getTrading()));
        } catch (RuntimeException exception) {
            LOGGER.warn("BGG-Abfrage für {} ist fehlgeschlagen: {}", gameName, exception.getMessage());
            return BggResult.withStatus(LookupStatus.ERROR);
        }
    }

    private SearchItem bestMatch(String gameName, List<SearchItem> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .filter(item -> item.getName() != null && item.getName().getValue() != null)
                .map(item -> new ScoredItem(item, normalizer.similarity(gameName, item.getName().getValue())))
                .filter(item -> item.score() >= MINIMUM_MATCH)
                .max(Comparator.comparingDouble(ScoredItem::score))
                .map(ScoredItem::item)
                .orElse(null);
    }

    private Thing firstThing(List<Thing> things) {
        return things == null || things.isEmpty() ? null : things.getFirst();
    }

    private BigDecimal decimalValue(DecimalValue value) {
        return value == null || value.getValue() == null
                ? null
                : BigDecimal.valueOf(value.getValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer intValue(IntValue value) {
        return value == null ? null : value.getValue();
    }

    private record ScoredItem(SearchItem item, double score) {
    }
}
