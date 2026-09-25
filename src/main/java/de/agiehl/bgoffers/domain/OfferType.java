package de.agiehl.bgoffers.domain;

public enum OfferType {
    STANDARD("Angebot"),
    GROUP_DEAL("Gruppendeal"),
    SPIELESCHMIEDE("Spieleschmiede"),
    FORUM_POST("Forenbeitrag");

    private final String displayName;

    OfferType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
