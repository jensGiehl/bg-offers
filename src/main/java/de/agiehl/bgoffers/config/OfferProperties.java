package de.agiehl.bgoffers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("offers")
public record OfferProperties(
        Sources sources,
        Http http,
        Schedule schedule,
        Telegram telegram,
        Bgg bgg) {

    public record Sources(
            URI spieleOffensive,
            URI milan,
            URI unknowns,
            URI unknownsLogin,
            String unknownsUsername,
            String unknownsPassword,
            URI priceComparison) {

        public boolean unknownsCredentialsConfigured() {
            return unknownsUsername != null
                    && !unknownsUsername.isBlank()
                    && unknownsPassword != null
                    && !unknownsPassword.isBlank();
        }
    }

    public record Http(
            Duration timeout,
            String userAgent,
            int maxAttempts,
            Duration retryDelay,
            int milanConcurrency) {
    }

    public record Schedule(Duration initialDelay, Duration crawlDelay, String healthCron) {
    }

    public record Telegram(String botToken, String chatId) {

        public boolean configured() {
            return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
        }
    }

    public record Bgg(String apiToken) {

        public boolean configured() {
            return apiToken != null && !apiToken.isBlank();
        }
    }
}
