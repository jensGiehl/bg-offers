package de.agiehl.bgoffers.scraper;

import java.net.URI;

public interface SessionDocumentClient extends DocumentClient {

    void login(URI loginUri, String username, String password);
}
