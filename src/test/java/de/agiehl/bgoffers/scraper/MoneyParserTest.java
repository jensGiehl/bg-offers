package de.agiehl.bgoffers.scraper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyParserTest {

    @Test
    void parsesGermanAndEnglishPrices() {
        assertThat(MoneyParser.parse("nur 29,95 €")).contains(new BigDecimal("29.95"));
        assertThat(MoneyParser.parse("EUR 18.50")).contains(new BigDecimal("18.50"));
    }

    @Test
    void rejectsTextWithoutPrice() {
        assertThat(MoneyParser.parse("ausverkauft")).isEmpty();
        assertThat(MoneyParser.parse(null)).isEmpty();
    }
}
