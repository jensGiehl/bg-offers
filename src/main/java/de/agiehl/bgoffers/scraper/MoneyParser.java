package de.agiehl.bgoffers.scraper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MoneyParser {

    private static final Pattern MONEY = Pattern.compile("(\\d{1,6}(?:[.,]\\d{1,2})?)");

    private MoneyParser() {
    }

    public static Optional<BigDecimal> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        var matcher = MONEY.matcher(value.replace("\u00a0", " "));
        if (!matcher.find()) {
            return Optional.empty();
        }
        var number = matcher.group(1);
        var normalized = number.contains(",") ? number.replace(".", "").replace(',', '.') : number;
        return Optional.of(new BigDecimal(normalized));
    }
}
