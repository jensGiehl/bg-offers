package de.agiehl.bgoffers.pricecomparison;

import de.agiehl.bgoffers.domain.LookupStatus;

import java.math.BigDecimal;

public record PriceComparisonResult(
        LookupStatus status,
        String url,
        BigDecimal availablePrice,
        BigDecimal bestPrice) {

    public static PriceComparisonResult withStatus(LookupStatus status) {
        return new PriceComparisonResult(status, null, null, null);
    }
}
