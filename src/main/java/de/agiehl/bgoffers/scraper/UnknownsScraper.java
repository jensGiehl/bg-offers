package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import org.jsoup.nodes.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "offers.sources",
        name = "unknowns-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class UnknownsScraper implements OfferScraper {

    private static final String THREAD_SELECTOR =
            "ol.wbbThread[data-thread-id] > li.columnSubject h3 "
                    + "> a.messageGroupLink.wbbTopicLink[href*=\"/forum/thread/\"]";

    private final SessionDocumentClient client;
    private final OfferProperties properties;

    public UnknownsScraper(SessionDocumentClient client, OfferProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public OfferSource source() {
        return OfferSource.UNKNOWNS;
    }

    @Override
    public List<ScrapedOffer> scrape() {
        if (!properties.sources().unknownsCredentialsConfigured()) {
            throw new SourceAccessException(
                    "unknowns.de benötigt UNKNOWNS_USERNAME und UNKNOWNS_PASSWORD");
        }
        client.login(
                properties.sources().unknownsLogin(),
                properties.sources().unknownsUsername(),
                properties.sources().unknownsPassword());
        return parseBoard(client.fetch(properties.sources().unknowns()));
    }

    List<ScrapedOffer> parseBoard(Document document) {
        rejectAccessDenied(document);
        var threads = new LinkedHashMap<String, String>();
        for (var anchor : document.select(THREAD_SELECTOR)) {
            var title = anchor.text().trim();
            var url = anchor.absUrl("href");
            if (!title.isBlank() && !url.isBlank()) {
                threads.putIfAbsent(url, title);
            }
        }
        if (threads.isEmpty()) {
            throw new SourceAccessException("Keine Forenbeiträge auf der unknowns.de-Schnäppchenseite gefunden");
        }
        return threads.entrySet().stream()
                .map(entry -> new ScrapedOffer(
                        source(),
                        OfferType.FORUM_POST,
                        entry.getValue(),
                        entry.getKey(),
                        null,
                        null,
                        null,
                        null,
                        null))
                .toList();
    }

    private void rejectAccessDenied(Document document) {
        var errorHeading = document.selectFirst("#tpl_wcf_error h1, body#tpl_wcf_error h1");
        if (errorHeading != null && errorHeading.text().toLowerCase(java.util.Locale.ROOT).contains("zugriff verweigert")) {
            throw new SourceAccessException(
                    "unknowns.de verweigert dem angemeldeten Account den Zugriff auf das Schnäppchenforum");
        }
    }
}
