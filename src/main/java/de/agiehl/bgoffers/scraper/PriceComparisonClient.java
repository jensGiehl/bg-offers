package de.agiehl.bgoffers.scraper;

import org.jsoup.nodes.Document;

import java.net.URI;

public sealed interface PriceComparisonClient permits PriceComparisonDocumentClient {

    Document search(URI searchUri);
}
