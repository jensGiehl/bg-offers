package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.TestProperties;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MilanScraperTest {

    @Test
    void readsListingAndLoadsFullDetailImage() {
        var listingUrl = "https://www.milan-spiele.de/angebote-c-103.html?perPage=250&sort=5d";
        var detailUrl = "https://www.milan-spiele.de/testspiel-p-123.html";
        var listing = Jsoup.parse("""
                <div class="productList">
                  <article class="product">
                    <div class="title"><a href="%s" alt="Testspiel">Testspiel</a></div>
                    <div class="productSpecialPrice">21,99 EUR</div>
                    <div class="delivery">sofort lieferbar</div>
                    <div class="picture"><img src="/thumb.jpg"></div>
                  </article>
                </div>
                """.formatted(detailUrl), listingUrl);
        var detail = Jsoup.parse("""
                <a class="highslide" href="/images/testspiel-large.jpg"><img src="/images/testspiel-small.jpg"></a>
                """, detailUrl);
        var client = new MapDocumentClient(Map.of(listingUrl, listing, detailUrl, detail));

        var offers = new MilanScraper(client, TestProperties.create()).scrape();

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.name()).isEqualTo("Testspiel");
            assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("21.99"));
            assertThat(offer.availability()).isEqualTo("sofort lieferbar");
            assertThat(offer.imageUrl()).isEqualTo("https://www.milan-spiele.de/images/testspiel-large.jpg");
        });
    }

    @Test
    void supportsDetailImageClassOnImageAndContainer() {
        var scraper = new MilanScraper(uri -> null, TestProperties.create());

        var image = Jsoup.parse("<img class='detail__image' src='/large.jpg'>", "https://shop.example/game");
        var container = Jsoup.parse("<a class='detail__image' href='/original.jpg'><img src='/small.jpg'></a>", "https://shop.example/game");

        assertThat(scraper.parseDetailImage(image)).isEqualTo("https://shop.example/large.jpg");
        assertThat(scraper.parseDetailImage(container)).isEqualTo("https://shop.example/small.jpg");
    }

    private record MapDocumentClient(Map<String, org.jsoup.nodes.Document> pages) implements DocumentClient {

        @Override
        public org.jsoup.nodes.Document fetch(URI uri) {
            var document = pages.get(uri.toString());
            if (document == null) {
                throw new SourceAccessException("Keine Testseite für " + uri);
            }
            return document;
        }
    }
}
