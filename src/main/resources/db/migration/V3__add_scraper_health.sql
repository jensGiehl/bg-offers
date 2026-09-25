CREATE TABLE scraper_health (
    source ENUM('BGG_MARKET', 'MILAN', 'SPIELE_OFFENSIVE', 'UNKNOWNS') PRIMARY KEY,
    monitoring_started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_new_data_at TIMESTAMP(6) WITH TIME ZONE,
    alert_sent BOOLEAN NOT NULL
);
