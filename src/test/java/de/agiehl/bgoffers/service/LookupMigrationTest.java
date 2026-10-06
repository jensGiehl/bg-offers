package de.agiehl.bgoffers.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class LookupMigrationTest {

    @Test
    void queuesIncompleteUnsentOffersIncludingPriceChangesWithoutResendingCurrentNotifications() throws Exception {
        var url = "jdbc:h2:mem:lookup-migration;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("6").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO offers (name, source, type, source_url, bgg_status, comparison_status,
                        first_seen_at, last_seen_at, last_changed_at, version, notification_fingerprint, notified_at)
                    VALUES
                        ('Neu', 'MILAN', 'STANDARD', 'https://shop.example/new', 'NOT_FOUND', 'ERROR',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, NULL, NULL),
                        ('Preis geändert', 'MILAN', 'STANDARD', 'https://shop.example/changed', 'FOUND', 'NOT_FOUND',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'previous-price',
                            TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00'),
                        ('Schon versendet', 'MILAN', 'STANDARD', 'https://shop.example/sent', 'NOT_FOUND', 'ERROR',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'current-price', CURRENT_TIMESTAMP),
                        ('Übersprungen', 'UNKNOWNS', 'FORUM_POST', 'https://shop.example/skipped', 'SKIPPED', 'SKIPPED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, NULL, NULL)
                    """);
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT name FROM offers WHERE next_lookup_at IS NOT NULL ORDER BY name")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("name")).isEqualTo("Neu");
            assertThat(result.next()).isTrue();
            assertThat(result.getString("name")).isEqualTo("Preis geändert");
            assertThat(result.next()).isFalse();
        }
    }
}
