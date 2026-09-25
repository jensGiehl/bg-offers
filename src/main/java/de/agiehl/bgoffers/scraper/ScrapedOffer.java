package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;

import java.math.BigDecimal;

public record ScrapedOffer(
        OfferSource source,
        OfferType type,
        String name,
        String sourceUrl,
        String imageUrl,
        BigDecimal price,
        String availability,
        Integer availableQuantity,
        Integer totalQuantity,
        String sourceOfferId,
        Integer bggId) {

    public ScrapedOffer(
            OfferSource source,
            OfferType type,
            String name,
            String sourceUrl,
            String imageUrl,
            BigDecimal price,
            String availability,
            Integer availableQuantity,
            Integer totalQuantity) {
        this(
                source,
                type,
                name,
                sourceUrl,
                imageUrl,
                price,
                availability,
                availableQuantity,
                totalQuantity,
                null,
                null);
    }
}
