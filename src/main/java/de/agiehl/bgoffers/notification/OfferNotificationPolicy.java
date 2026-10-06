package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class OfferNotificationPolicy {

    private static final BigDecimal BEST_PRICE_FACTOR = new BigDecimal("1.10");

    private OfferNotificationPolicy() {
    }

    public static Decision evaluate(Offer offer) {
        if (offer.getSource() == OfferSource.UNKNOWNS) {
            return new Decision(true, false, "Beiträge von unknowns.de werden unabhängig vom Preisvergleich gemeldet.");
        }
        var bestPrice = offer.getComparisonBestPrice();
        if (bestPrice != null) {
            var limit = bestPrice.multiply(BEST_PRICE_FACTOR);
            if (offer.getPrice() == null) {
                return new Decision(false, true, "Bei bekanntem Bestpreis fehlt der Angebotspreis.");
            }
            if (offer.getPrice().compareTo(limit) > 0) {
                return new Decision(false, true,
                        "Angebotspreis %s überschreitet 110 %% des historischen Bestpreises %s (Grenze: %s)."
                                .formatted(money(offer.getPrice()), money(bestPrice), money(limit.setScale(2, RoundingMode.DOWN))));
            }
        } else {
            return new Decision(true, false, "Kein historischer Bestpreis verfügbar; die Preisregeln erlauben die Meldung.");
        }
        if (offer.getType() == OfferType.SPIELESCHMIEDE || offer.getType() == OfferType.FORUM_POST) {
            return new Decision(true, false, "Dieser Angebotstyp wird ohne weiteren Vergleichspreisfilter gemeldet; die Bestpreisgrenze ist eingehalten.");
        }
        var cheaper = offer.getPrice() != null && offer.getComparisonAvailablePrice() != null
                && offer.getPrice().compareTo(offer.getComparisonAvailablePrice()) < 0;
        if (cheaper) {
            return new Decision(true, false,
                    "Angebotspreis %s liegt unter dem verfügbaren Vergleichspreis %s; die Bestpreisgrenze (+10 %% auf %s) ist eingehalten."
                            .formatted(money(offer.getPrice()), money(offer.getComparisonAvailablePrice()), money(bestPrice)));
        }
        if (offer.getSource() != OfferSource.BGG_MARKET
                && (offer.getBggStatus() != LookupStatus.FOUND || offer.getComparisonStatus() != LookupStatus.FOUND)) {
            return new Decision(true, false, "Recherche unvollständig; die Meldung ist nach Abschluss der Suchversuche erlaubt und die Bestpreisgrenze eingehalten.");
        }
        return new Decision(false, false, offer.getComparisonAvailablePrice() == null
                ? "Kein verfügbarer Vergleichspreis vorhanden; für dieses Angebot ist ein günstigerer Preis erforderlich."
                : "Angebotspreis %s ist nicht günstiger als der verfügbare Vergleichspreis %s."
                        .formatted(money(offer.getPrice()), money(offer.getComparisonAvailablePrice())));
    }

    private static String money(BigDecimal value) {
        return value == null ? "nicht angegeben" : String.format(Locale.GERMANY, "%.2f €", value);
    }

    public record Decision(boolean eligible, boolean bestPriceWithheld, String reason) {
    }
}
