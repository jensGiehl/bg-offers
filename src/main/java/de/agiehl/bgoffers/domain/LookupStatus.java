package de.agiehl.bgoffers.domain;

public enum LookupStatus {
    NOT_REQUESTED("Noch nicht abgefragt"),
    FOUND("Gefunden"),
    NOT_FOUND("Nicht gefunden"),
    NOT_CONFIGURED("Nicht konfiguriert"),
    ERROR("Fehler"),
    SKIPPED("Nicht erforderlich");

    private final String displayName;

    LookupStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
