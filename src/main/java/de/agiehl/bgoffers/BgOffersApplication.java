package de.agiehl.bgoffers;

import de.agiehl.bgoffers.config.OfferProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(OfferProperties.class)
public class BgOffersApplication {

    public static void main(String[] args) {
        SpringApplication.run(BgOffersApplication.class, args);
    }
}
