package de.agiehl.bgoffers.enrichment;

import de.agiehl.bgoffers.domain.LookupStatus;

import java.math.BigDecimal;

public record BggResult(
        LookupStatus status,
        Integer id,
        BigDecimal rating,
        Integer wantToBuy,
        Integer wantInTrade) {

    public static BggResult withStatus(LookupStatus status) {
        return new BggResult(status, null, null, null, null);
    }
}
