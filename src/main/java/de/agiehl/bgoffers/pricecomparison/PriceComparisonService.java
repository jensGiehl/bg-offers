package de.agiehl.bgoffers.pricecomparison;

import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PriceComparisonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceComparisonService.class);

    private final PriceComparisonClient client;
    private final GameNameNormalizer normalizer;

    public PriceComparisonService(PriceComparisonClient client, GameNameNormalizer normalizer) {
        this.client = client;
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
            var searchTerm = normalizer.priceComparisonSearchTerm(gameName);
            if (searchTerm.isBlank()) {
                return PriceComparisonResult.withStatus(LookupStatus.NOT_FOUND);
            }
            LOGGER.debug("Preisvergleich für {}: Service-Abfrage mit Suchbegriff '{}'", gameName, searchTerm);
            return client.lookup(searchTerm, bggId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Preisvergleich für {} ist fehlgeschlagen: {}", gameName, exception.getMessage());
            return PriceComparisonResult.withStatus(LookupStatus.ERROR);
        }
    }

    public boolean healthCheck() {
        var result = lookup("Scythe");
        return result.status() == LookupStatus.FOUND && result.availablePrice() != null;
    }
}
