package de.agiehl.bgoffers.pricecomparison;

public sealed interface PriceComparisonClient permits PriceComparisonHttpClient {

    PriceComparisonResult lookup(String name, Integer bggId);
}
