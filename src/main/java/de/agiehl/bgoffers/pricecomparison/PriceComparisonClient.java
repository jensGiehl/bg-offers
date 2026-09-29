package de.agiehl.bgoffers.pricecomparison;

import org.jsoup.nodes.Document;

import java.net.URI;

public sealed interface PriceComparisonClient permits PriceComparisonDocumentClient {

    Document search(URI searchUri, Integer bggId);

    default Document search(URI searchUri) {
        return search(searchUri, null);
    }
}
