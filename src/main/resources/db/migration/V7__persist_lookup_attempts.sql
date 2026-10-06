ALTER TABLE offers ADD COLUMN next_lookup_at TIMESTAMP(6) WITH TIME ZONE;
CREATE INDEX idx_offer_next_lookup ON offers(next_lookup_at);

CREATE TABLE offer_lookup_attempts (
    offer_id BIGINT NOT NULL,
    attempt_index INTEGER NOT NULL,
    target ENUM('BGG', 'COMPARISON') NOT NULL,
    search_term VARCHAR(500) NOT NULL,
    attempted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status ENUM('ERROR', 'FOUND', 'NOT_CONFIGURED', 'NOT_FOUND', 'NOT_REQUESTED', 'SKIPPED') NOT NULL,
    PRIMARY KEY (offer_id, attempt_index),
    CONSTRAINT fk_lookup_attempt_offer FOREIGN KEY (offer_id) REFERENCES offers(id)
);

UPDATE offers SET next_lookup_at = CURRENT_TIMESTAMP
WHERE (notified_at IS NULL OR notified_at < last_changed_at)
    AND (bgg_status IN ('ERROR', 'NOT_FOUND', 'NOT_REQUESTED')
        OR comparison_status IN ('ERROR', 'NOT_FOUND', 'NOT_REQUESTED')
        OR (comparison_status = 'FOUND' AND comparison_best_price IS NULL));
