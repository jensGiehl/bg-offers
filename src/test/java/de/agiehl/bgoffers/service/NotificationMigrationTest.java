package de.agiehl.bgoffers.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMigrationTest {

    @Test
    void preservesExistingDeliveryDataAndSupportsAssociatedFailuresAfterUpgrade() throws Exception {
        var url = "jdbc:h2:mem:notification-migration;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("7").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO offers (name, source, type, source_url, bgg_status, comparison_status,
                        first_seen_at, last_seen_at, last_changed_at, version, notification_fingerprint, notified_at)
                    VALUES ('Altdaten', 'BGG_MARKET', 'STANDARD', 'https://shop.example/legacy', 'FOUND', 'FOUND',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'sent-price', CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO activity_log (type, occurred_at, detail, notification_sent)
                    VALUES ('TELEGRAM_FAILED', CURRENT_TIMESTAMP, 'HTTP 400', FALSE)
                    """);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT * FROM offers")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("notification_status")).isEqualTo("UNKNOWN");
                assertThat(result.getInt("notification_image_failures")).isZero();
                assertThat(result.getString("notification_reason")).isNull();
                assertThat(result.getString("notification_fingerprint")).isEqualTo("sent-price");
                assertThat(result.getObject("notified_at")).isNotNull();
            }
            try (var result = statement.executeQuery("SELECT * FROM activity_log")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("detail")).isEqualTo("HTTP 400");
                assertThat(result.getBoolean("notification_sent")).isFalse();
            }
            assertThat(statement.executeUpdate("""
                    INSERT INTO activity_log (type, occurred_at, offer_id, detail, notification_sent)
                    VALUES ('NOTIFICATION_DEFERRED', CURRENT_TIMESTAMP, 1, 'Initialimport-Pause', FALSE)
                    """)).isEqualTo(1);
            assertThat(statement.executeUpdate("""
                    INSERT INTO activity_log (type, occurred_at, offer_id, detail, notification_sent)
                    VALUES ('NOTIFICATION_WITHHELD', CURRENT_TIMESTAMP, 1, 'Vergleichspreis nicht unterschritten', FALSE)
                    """)).isEqualTo(1);
        }
    }
}
