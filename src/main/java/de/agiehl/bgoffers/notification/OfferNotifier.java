package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.domain.Offer;

public interface OfferNotifier {

    boolean sendOffer(Offer offer);

    boolean sendSystemCheck(boolean successful, String message);

    boolean sendHealthAlert(String message);

    boolean sendHealthRecovery(String message);
}
