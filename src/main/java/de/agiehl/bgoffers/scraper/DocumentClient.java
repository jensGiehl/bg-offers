package de.agiehl.bgoffers.scraper;

import org.jsoup.nodes.Document;

import java.net.URI;
import java.util.Map;

public interface DocumentClient {

    Document fetch(URI uri);

    default Document fetch(URI uri, Map<String, String> headers) {
        return fetch(uri);
    }

    default String fetchJson(URI uri) {
        throw new UnsupportedOperationException("JSON-Abrufe werden von diesem Client nicht unterstützt");
    }
}
