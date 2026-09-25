package de.agiehl.bgoffers;

import de.agiehl.bgoffers.config.OfferProperties;

import java.net.URI;
import java.time.Duration;

public final class TestProperties {

    private TestProperties() {
    }

    public static OfferProperties create() {
        return create(false);
    }

    public static OfferProperties create(boolean initialImport) {
        return new OfferProperties(
                new OfferProperties.Sources(
                        URI.create("https://www.spiele-offensive.de/"),
                        URI.create("https://www.milan-spiele.de/angebote-c-103.html?perPage=250&sort=5d"),
                        URI.create("https://unknowns.de/forum/board/43-schn%C3%A4ppchen/"),
                        URI.create("https://unknowns.de/login/"),
                        "test-user",
                        "test-password",
                        URI.create("https://www.brettspiel-angebote.de/")),
                new OfferProperties.Http(Duration.ofSeconds(5), "test", 3, Duration.ZERO, 2),
                new OfferProperties.Schedule(Duration.ZERO, Duration.ofMinutes(5), "0 0 8 * * *"),
                initialImport,
                new OfferProperties.Telegram("", ""),
                new OfferProperties.Bgg(""));
    }
}
