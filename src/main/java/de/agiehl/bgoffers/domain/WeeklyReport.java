package de.agiehl.bgoffers.domain;

import java.time.Instant;
import java.util.List;

public record WeeklyReport(Instant from, Instant until, long found, long sent, List<WithheldOffer> withheld) {

    public WeeklyReport {
        withheld = List.copyOf(withheld);
    }

    public record WithheldOffer(String name, String url) {
    }
}
