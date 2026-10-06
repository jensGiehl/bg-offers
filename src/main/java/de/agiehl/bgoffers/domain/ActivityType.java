package de.agiehl.bgoffers.domain;

public enum ActivityType {
    APPLICATION_STARTED("Anwendung gestartet"),
    OFFER_FOUND("Neues Angebot"),
    PRICE_CHANGED("Preis geändert"),
    OFFER_SENT("Angebot versendet"),
    BEST_PRICE_WITHHELD("Bestpreisgrenze nicht erreicht"),
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
