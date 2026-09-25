package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.OfferType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpieleOffensiveScraperTest {

    private static final String BASE = "https://www.spiele-offensive.de/";

    @Test
    void readsProductGroupDealAndSpieleschmiedeWhileIgnoringInspirationsTv() {
        var productUrl = BASE + "Spiel/Ein-Spiel-100.html";
        var groupUrl = BASE + "index.php?cmd=gruppendeal&grid=9212";
        var forgeUrl = BASE + "Spieleschmiede/Dice-Throne-Adventures-2";
        var landingPage = document("""
                <div id="wrapper_startseite">
                  <a href="%s">
                    <img src="/gfx/bilder/display_angebot04.png">
                    <img src="/gfx/bilder/display_angebot02.png">
                    <div><img src="https://mediaservice.happyshops.com/ANY/Article/000001024027/1024027.jpg"></div>
                  </a>
                  <a href="%s"><img src="/banner/group.jpg"></a>
                  <a href="%s"><img src="/banner/forge.jpg" alt="Jetzt in der Spieleschmiede: Dice Throne Adventures 2"></a>
                  <a href="/Inspirations-TV"><img src="/banner/tv.jpg"></a>
                </div>
                """.formatted(productUrl, groupUrl, forgeUrl), BASE);
        var client = new MapDocumentClient(Map.of(
                productUrl, document("""
                        <h1>Assault on Doomrock</h1>
                        <div><div>versandkostenfrei in Deutschland sofort lieferbar oder später</div><div class="sellw"></div></div>
                        <div class="preis">statt 49,99 € jetzt nur 34,95 €</div>
                        """, productUrl),
                groupUrl, document("""
                        <h1>Der große Gruppendeal</h1>
                        <form id="gdShoppingCartForm">
                          <input name="uebergabe[1][1]" value="12345">
                          <input class="toChange" name="uebergabe[1][3]" value="24.90">
                        </form>
                        <div class="gruppendealPrice"><span>24,<span>90</span></span> €</div>
                        <p>Noch verfügbar 7 von 20</p>
                        """, groupUrl)));

        var offers = new SpieleOffensiveScraper(client, TestProperties.create()).parseLandingPage(landingPage);

        assertThat(offers).hasSize(3);
        assertThat(offers).filteredOn(offer -> offer.type() == OfferType.STANDARD)
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.name()).isEqualTo("Assault on Doomrock");
                    assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("34.95"));
                    assertThat(offer.imageUrl()).isEqualTo(
                            "https://mediaservice.happyshops.com/ANY/Article/000001024027/1024027.jpg");
                    assertThat(offer.availability()).isEqualTo("sofort lieferbar");
                });
        assertThat(offers).filteredOn(offer -> offer.type() == OfferType.GROUP_DEAL)
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("24.90"));
                    assertThat(offer.availableQuantity()).isEqualTo(7);
                    assertThat(offer.totalQuantity()).isEqualTo(20);
                    assertThat(offer.imageUrl()).isEqualTo(BASE + "banner/group.jpg");
                });
        assertThat(offers).filteredOn(offer -> offer.type() == OfferType.SPIELESCHMIEDE)
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.name()).isEqualTo("Dice Throne Adventures 2");
                    assertThat(offer.price()).isNull();
                    assertThat(offer.imageUrl()).isEqualTo(BASE + "banner/forge.jpg");
                });
    }

    @Test
    void usesBannerDataWhenAProductDetailCannotBeLoaded() {
        var url = BASE + "Spiel/Fallback-42.html";
        var landingPage = document("""
                <div id="wrapper_startseite">
                  <a href="%s" onclick="track(this, `Fallback-Spiel`, 19.95);"><img src="/fallback.jpg"></a>
                </div>
                """.formatted(url), BASE);

        var offers = new SpieleOffensiveScraper(uri -> {
            throw new SourceAccessException("nicht erreichbar");
        }, TestProperties.create()).parseLandingPage(landingPage);

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.name()).isEqualTo("Fallback-Spiel");
            assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("19.95"));
        });
    }

    @Test
    void readsGroupDealCheckoutPriceInsteadOfUnrelatedOfferPrices() {
        var groupUrl = BASE + "index.php?cmd=gruppendeal&grid=13313";
        var landingPage = document("""
                <div id="wrapper_startseite">
                  <a href="%s"><img src="/banner/seek-and-suit.jpg"></a>
                </div>
                """.formatted(groupUrl), BASE);
        var detailPage = document("""
                <section id="specialOfferContainer">
                  <div class="gruppendealPrice"><span>20,<span>00</span></span> €</div>
                </section>
                <h1>SEAk and Suit</h1>
                <form id="gdShoppingCartForm">
                  <input name="uebergabe[1][1]" value="1031874">
                  <input class="toChange" name="uebergabe[1][3]" value="11.99">
                  <input class="toChange" name="uebergabe[1][7]" value="4df28df15de7553f7ca8">
                </form>
                <div class="gruppendealPrice"><span>11,<span>99</span></span> €</div>
                <div class="vipUpgradeSuggestion">1 Jahr VIP-Upgrade für 20,00 € hinzufügen</div>
                <p>Noch verfügbar 9 von 10</p>
                """, groupUrl);
        var scraper = new SpieleOffensiveScraper(
                new MapDocumentClient(Map.of(groupUrl, detailPage)),
                TestProperties.create());

        var offers = scraper.parseLandingPage(landingPage);

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.name()).isEqualTo("SEAk and Suit");
            assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("11.99"));
            assertThat(offer.availableQuantity()).isEqualTo(9);
            assertThat(offer.totalQuantity()).isEqualTo(10);
        });
    }

    private static Document document(String html, String baseUri) {
        return Jsoup.parse(html, baseUri);
    }

    private record MapDocumentClient(Map<String, Document> pages) implements DocumentClient {

        @Override
        public Document fetch(URI uri) {
            var document = pages.get(uri.toString());
            if (document == null) {
                throw new SourceAccessException("Keine Testseite für " + uri);
            }
            return document;
        }
    }
}
