CREATE TABLE external_health_check (
    check_name ENUM('BOARD_GAME_GEEK', 'PRICE_COMPARISON') PRIMARY KEY,
    recovery_notification_pending BOOLEAN NOT NULL
);
