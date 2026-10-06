package de.agiehl.bgoffers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
public class LookupAttempt {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LookupTarget target;

    @Column(nullable = false, length = 500)
    private String searchTerm;

    @Column(nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LookupStatus status;

    protected LookupAttempt() {
    }

    public LookupAttempt(LookupTarget target, String searchTerm, Instant attemptedAt, LookupStatus status) {
        this.target = target;
        this.searchTerm = searchTerm;
        this.attemptedAt = attemptedAt;
        this.status = status;
    }

    public LookupTarget getTarget() {
        return target;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public LookupStatus getStatus() {
        return status;
    }
}
