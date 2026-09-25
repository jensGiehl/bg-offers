package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class BggMarketScraperTest {

    @Test
    void readsProductsWithTheirMarketAndBggIdentifiers() {
        var properties = TestProperties.create();
        var json = """
                {
                  "products": [
                    {
                      "productid": "4147596",
                      "objectid": "350458",
                      "producthref": "/market/product/4147596",
                      "price": "30.00",
                      "version": {
                        "name": "Terrakotta-Armee (German edition)",
                        "imageSets": {
                          "mediacard": {
                            "src": "https://cf.geekdo-images.com/terrakotta.jpg"
                          }
                        }
                      }
                    },
                    {
                      "productid": "4147592",
                      "objectid": "397931",
                      "producthref": "/market/product/4147592",
                      "price": "49.00",
                      "version": {
                        "name": "Deep Regrets (German edition)"
                      }
                    },
                    {
                      "productid": "4147557",
                      "objectid": "221107",
                      "producthref": "/market/product/4147557",
                      "price": "48.00",
                      "objectlink": {
                        "name": "Pandemic Legacy: Season 2"
                      }
                    }
                  ],
                  "config": {
                    "numitems": 37150,
                    "itemsperpage": 50
                  }
                }
                """;
        var client = new JsonDocumentClient(properties.sources().bggMarket(), json);
        var scraper = new BggMarketScraper(client, properties, new ObjectMapper());

        var offers = scraper.scrape();

        assertThat(offers).hasSize(3);
        assertThat(offers.getFirst()).satisfies(offer -> {
            assertThat(offer.source()).isEqualTo(OfferSource.BGG_MARKET);
            assertThat(offer.sourceOfferId()).isEqualTo("4147596");
            assertThat(offer.bggId()).isEqualTo(350458);
            assertThat(offer.name()).isEqualTo("Terrakotta-Armee (German edition)");
            assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(offer.sourceUrl()).isEqualTo("https://boardgamegeek.com/market/product/4147596");
            assertThat(offer.imageUrl()).isEqualTo("https://cf.geekdo-images.com/terrakotta.jpg");
        });
        assertThat(offers.get(1).imageUrl()).isNull();
        assertThat(offers.get(2).name()).isEqualTo("Pandemic Legacy: Season 2");
    }

    @Test
    void skipsIncompleteProductsAndDeduplicatesByProductId() {
        var properties = TestProperties.create();
        var json = """
                {
                  "products": [
                    {
                      "productid": "100",
                      "objectid": "42",
                      "producthref": "/market/product/100",
                      "price": "10.50",
                      "version": {"name": "Testspiel"}
                    },
                    {
                      "productid": "100",
                      "objectid": "42",
                      "producthref": "/market/product/100",
                      "price": "11.50",
                      "version": {"name": "Doppelter Eintrag"}
                    },
                    {
                      "productid": "101",
                      "objectid": "43",
                      "producthref": "/market/product/101",
                      "version": {"name": "Ohne Preis"}
                    }
                  ]
                }
                """;
        var scraper = new BggMarketScraper(
                new JsonDocumentClient(properties.sources().bggMarket(), json),
                properties,
                new ObjectMapper());

        var offers = scraper.scrape();

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.sourceOfferId()).isEqualTo("100");
            assertThat(offer.name()).isEqualTo("Testspiel");
        });
    }

    private record JsonDocumentClient(URI expectedUri, String response) implements DocumentClient {

        @Override
        public Document fetch(URI uri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String fetchJson(URI uri) {
            assertThat(uri).isEqualTo(expectedUri);
            return response;
        }
    }
}
