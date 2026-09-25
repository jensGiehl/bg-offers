package de.agiehl.bgoffers.scraper;

public class SourceAccessException extends RuntimeException {

    public SourceAccessException(String message) {
        super(message);
    }

    public SourceAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
