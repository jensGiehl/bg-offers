package de.agiehl.bgoffers.domain;

public enum ActivityType {
    OFFER_FOUND("Neues Angebot"),
    PRICE_CHANGED("Preis geändert"),
    HTTP_RETRY("HTTP-Abruf wird wiederholt"),
    LOOKUP_RETRY("Recherche wird wiederholt"),
    TELEGRAM_SENT("Telegram-Nachricht versendet"),
    TELEGRAM_FAILED("Telegram-Versand fehlgeschlagen");

    private final String displayName;

    ActivityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
