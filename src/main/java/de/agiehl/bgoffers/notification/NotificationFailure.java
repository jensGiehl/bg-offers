package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.config.OfferProperties;

public final class NotificationFailure {

    private NotificationFailure() {
    }

    public static String detail(String detail, OfferProperties.Telegram telegram) {
        var sanitized = detail == null || detail.isBlank() ? "Unbekannter technischer Versandfehler" : detail;
        if (telegram.botToken() != null && !telegram.botToken().isBlank()) {
            sanitized = sanitized.replace(telegram.botToken(), "[Token entfernt]");
        }
        if (telegram.chatId() != null && !telegram.chatId().isBlank()) {
            sanitized = sanitized.replace(telegram.chatId(), "[Chat entfernt]");
        }
        return sanitized.length() > 1000 ? sanitized.substring(0, 999) + "…" : sanitized;
    }
}
