package de.agiehl.bgoffers.domain;

public enum NotificationStatus {
    UNKNOWN("Versandentscheidung unbekannt"),
    WAITING_LOOKUPS("Recherche läuft"),
    INITIAL_IMPORT_PAUSED("Initialimport-Pause"),
    WITHHELD("Nicht versendet"),
    DUPLICATE("Bereits gemeldet"),
    SENT("Telegram-Nachricht versendet"),
    CONSOLE_ONLY("Nur im Anwendungslog ausgegeben"),
    FAILED("Telegram-Versand fehlgeschlagen");

    private final String displayName;

    NotificationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
