package de.agiehl.bgoffers.pricecomparison;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class PriceComparisonDiagnostics {

    private PriceComparisonDiagnostics() {
    }

    public static int run() throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        var properties = diagnosticProperties();
        System.out.printf("Java: %s, Vendor: %s%n", Runtime.version(), System.getProperty("java.vendor"));
        System.out.printf("System: %s %s%n", System.getProperty("os.name"), System.getProperty("os.arch"));
        System.out.printf("IPv4-Stack erzwungen: %s%n", System.getProperty("java.net.preferIPv4Stack", "false"));
        var origin = properties.sources().priceComparison();
        System.out.printf("Preisvergleich: %s%n", origin);
        System.out.printf("User-Agent: %s%n", properties.http().userAgent());
        System.out.println("Client: Apache HttpClient Classic / HTTP/1.1, IPv6 bevorzugt");
        System.out.println("DNS-Reihenfolge (keine Aussage zur tatsächlich verwendeten Adresse):");
        for (var address : new Ipv6FirstDnsResolver().resolve(origin.getHost())) {
            System.out.printf("  %s%n", address.getHostAddress());
        }
        var service = new PriceComparisonService(
                new PriceComparisonDocumentClient(properties, PriceComparisonDiagnostics::printConnection),
                properties, new GameNameNormalizer());
        var result = service.lookup("Scythe", 169786);
        System.out.printf("Ergebnis: %s, URL: %s, verfügbarer Preis: %s%n",
                result.status(), result.url(), result.availablePrice());
        return result.status() == LookupStatus.FOUND ? 0 : 2;
    }

    private static void printConnection(PriceComparisonHttpClient.ConnectionDetails connection) {
        System.out.printf(
                "Verbindung: HTTP=%d, Protokoll=%s, Lokal=%s, Ziel=%s, TLS=%s, Cipher=%s, CDN-Request-ID=%s%n",
                connection.statusCode(), connection.protocol(), connection.localAddress(), connection.remoteAddress(),
                connection.tlsProtocol(), connection.cipherSuite(), connection.requestId());
    }

    private static OfferProperties diagnosticProperties() throws IOException {
        var environment = new StandardEnvironment();
        var propertySources = new YamlPropertySourceLoader().load(
                "diagnostics", new ClassPathResource("application.yml"));
        propertySources.forEach(source -> environment.getPropertySources().addLast(source));
        var defaults = Binder.get(environment).bind("offers", OfferProperties.class).get();
        var http = defaults.http();
        return new OfferProperties(
                defaults.sources(),
                new OfferProperties.Http(http.timeout(), http.userAgent(), 1, Duration.ZERO, Duration.ZERO,
                        http.milanConcurrency()),
                defaults.schedule(), defaults.sourceHealth(), false, defaults.commitId(),
                defaults.telegram(), defaults.bgg());
    }
}
