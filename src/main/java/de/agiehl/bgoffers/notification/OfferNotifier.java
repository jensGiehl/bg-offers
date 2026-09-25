package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.domain.Offer;

public interface OfferNotifier {

    boolean sendOffer(Offer offer);

    boolean sendHealthAlert(String message);
}
