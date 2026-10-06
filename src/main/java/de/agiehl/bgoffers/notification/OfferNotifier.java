package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.WeeklyReport;

public interface OfferNotifier {

    boolean sendOffer(Offer offer);

    boolean sendWeeklyReport(WeeklyReport report);

    boolean sendSystemCheck(boolean successful, String message);

    boolean sendHealthAlert(String message);

    boolean sendHealthRecovery(String message);
}
