package de.agiehl.bgoffers.domain;

public enum OfferSource {
    SPIELE_OFFENSIVE("Spiele-Offensive", "SO"),
    MILAN("Milan-Spiele", "Milan"),
    BGG_MARKET("BGG Market", "BGG"),
    UNKNOWNS("unknowns.de", null);

    public static final String UNKNOWNS_LOGO_URL = "https://unknowns.de/images/style-10/pageLogo-5cc3ef36.svg";

    private final String displayName;
    private final String badgeLabel;

    OfferSource(String displayName, String badgeLabel) {
        this.displayName = displayName;
        this.badgeLabel = badgeLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeLabel() {
        return badgeLabel;
    }
}
