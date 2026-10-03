package de.agiehl.bgoffers;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.pricecomparison.PriceComparisonDiagnostics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.util.Arrays;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(OfferProperties.class)
public class BgOffersApplication {

    public static void main(String[] args) throws IOException {
        if (Arrays.asList(args).contains("--diagnose-price-comparison")) {
            System.exit(PriceComparisonDiagnostics.run());
            return;
        }
        SpringApplication.run(BgOffersApplication.class, args);
    }
}
