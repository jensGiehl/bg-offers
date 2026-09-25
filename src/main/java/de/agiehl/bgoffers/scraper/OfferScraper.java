package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.domain.OfferSource;

import java.util.List;

public interface OfferScraper {

    OfferSource source();

    List<ScrapedOffer> scrape();
}
