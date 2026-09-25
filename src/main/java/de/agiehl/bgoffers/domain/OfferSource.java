package de.agiehl.bgoffers.domain;

public enum OfferSource {
    SPIELE_OFFENSIVE("Spiele-Offensive"),
    MILAN("Milan-Spiele"),
    BGG_MARKET("BGG Market"),
    UNKNOWNS("unknowns.de");

    private final String displayName;

    OfferSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
