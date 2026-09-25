package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import de.agiehl.bgoffers.service.ActivityLogService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelegramOrConsoleNotifierTest {

    @Test
    void omitsUnavailableEnrichmentAndAvailabilityFields() {
        var offer = offer();
        offer.setPrice(new BigDecimal("19.99"));
        offer.setAvailability("Keine Angabe");
        var notifier = new TelegramOrConsoleNotifier(
                TestProperties.create(), mock(ActivityLogService.class));

        var html = notifier.htmlText(offer);
        var plainText = notifier.plainText(offer);

        assertThat(html)
                .contains("19,99 €", "Angebot")
                .doesNotContain("Preisvergleich", "Vergleich:", "Bestpreis", "BGG", "Kaufen", "Tausch", "📦");
        assertThat(plainText)
                .contains("Preis: 19,99 €", "URL: https://shop.example/offer")
                .doesNotContain("Verfügbarkeit", "Vergleich", "Bestpreis", "BGG", "Want to buy", "Want in trade");
    }

    @Test
    void rendersOnlyTheIndividualEnrichmentValuesThatExist() {
        var offer = offer();
        offer.setAvailability("sofort lieferbar");
        offer.setComparisonAvailablePrice(new BigDecimal("24.99"));
        offer.setBggRating(new BigDecimal("7.80"));
        var notifier = new TelegramOrConsoleNotifier(
                TestProperties.create(), mock(ActivityLogService.class));

        var html = notifier.htmlText(offer);

        assertThat(html)
                .contains("sofort lieferbar", "Vergleich: 24,99 €", "BGG: 7.8")
                .doesNotContain("Bestpreis", "Kaufen", "Tausch");
    }

    @Test
    void rendersForumPostsWithOnlyTitleAndLink() {
        var offer = Offer.create(
                OfferSource.UNKNOWNS,
                OfferType.FORUM_POST,
                "Ein <gutes> Schnäppchen",
                "https://unknowns.de/forum/thread/42-ein-gutes-schnaeppchen/",
                Instant.parse("2026-09-25T10:00:00Z"));
        var notifier = new TelegramOrConsoleNotifier(
                TestProperties.create(), mock(ActivityLogService.class));

        assertThat(notifier.htmlText(offer)).isEqualTo("""
                <b>Ein &lt;gutes&gt; Schnäppchen</b>
                <a href="https://unknowns.de/forum/thread/42-ein-gutes-schnaeppchen/">Beitrag öffnen</a>""");
        assertThat(notifier.plainText(offer)).isEqualTo(String.join(
                System.lineSeparator(),
                "Ein <gutes> Schnäppchen",
                "https://unknowns.de/forum/thread/42-ein-gutes-schnaeppchen/"));
    }

    private Offer offer() {
        return Offer.create(
                OfferSource.MILAN,
                OfferType.STANDARD,
                "Testspiel",
                "https://shop.example/offer",
                Instant.parse("2026-09-25T10:00:00Z"));
    }
}
