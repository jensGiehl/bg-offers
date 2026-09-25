package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.TestProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnknownsScraperTest {

    private final UnknownsScraper scraper = new UnknownsScraper(
            mock(SessionDocumentClient.class), TestProperties.create());

    @Test
    void readsOnlyThreadTitlesAndLinksFromTheBoard() {
        var document = Jsoup.parse("""
                <ol id="thread320208" class="messageGroup wbbThread" data-thread-id="320208">
                  <li class="columnSubject">
                    <h3>
                      <a href="/forum/thread/320208-gutes-angebot/" class="messageGroupLink wbbTopicLink">Gutes Angebot</a>
                      <span class="badge messageGroupCounterMobile">
                        <a href="/forum/thread/320208-gutes-angebot/?action=lastPost">47</a>
                      </span>
                    </h3>
                  </li>
                </ol>
                <ol id="thread319795" class="messageGroup wbbThread" data-thread-id="319795">
                  <li class="columnSubject">
                    <h3>
                      <a href="/forum/thread/319795-noch-ein-deal/" class="messageGroupLink wbbTopicLink">Noch ein Deal</a>
                      <span class="badge messageGroupCounterMobile">
                        <a href="/forum/thread/319795-noch-ein-deal/?action=lastPost">3</a>
                      </span>
                    </h3>
                  </li>
                </ol>
                <a href="/forum/thread/99999-kein-listeneintrag/">Navigation</a>
                """, "https://unknowns.de/forum/board/43-schn%C3%A4ppchen/");

        var offers = scraper.parseBoard(document);

        assertThat(offers).hasSize(2).allSatisfy(offer -> {
            assertThat(offer.source()).isEqualTo(OfferSource.UNKNOWNS);
            assertThat(offer.type()).isEqualTo(OfferType.FORUM_POST);
            assertThat(offer.imageUrl()).isNull();
            assertThat(offer.price()).isNull();
            assertThat(offer.availability()).isNull();
            assertThat(offer.availableQuantity()).isNull();
            assertThat(offer.totalQuantity()).isNull();
        });
        assertThat(offers.getFirst().name()).isEqualTo("Gutes Angebot");
        assertThat(offers.getFirst().sourceUrl())
                .isEqualTo("https://unknowns.de/forum/thread/320208-gutes-angebot/");
    }

    @Test
    void reportsTheLoginRequirementInsteadOfReturningAnEmptyResult() {
        var document = Jsoup.parse("""
                <body id="tpl_wcf_error"><h1>Zugriff verweigert</h1></body>
                """, "https://unknowns.de/forum/board/43-schn%C3%A4ppchen/");

        assertThatThrownBy(() -> scraper.parseBoard(document))
                .isInstanceOf(SourceAccessException.class)
                .hasMessageContaining("angemeldeten Account");
    }

    @Test
    void logsInBeforeFetchingTheBoard() {
        var client = mock(SessionDocumentClient.class);
        var properties = TestProperties.create();
        var document = Jsoup.parse("""
                <ol class="wbbThread" data-thread-id="123">
                  <li class="columnSubject">
                    <h3><a class="messageGroupLink wbbTopicLink" href="/forum/thread/123-deal/">Deal</a></h3>
                  </li>
                </ol>
                """, properties.sources().unknowns().toString());
        when(client.fetch(properties.sources().unknowns())).thenReturn(document);
        var authenticatedScraper = new UnknownsScraper(client, properties);

        authenticatedScraper.scrape();

        var requests = inOrder(client);
        requests.verify(client).login(
                properties.sources().unknownsLogin(),
                properties.sources().unknownsUsername(),
                properties.sources().unknownsPassword());
        requests.verify(client).fetch(properties.sources().unknowns());
    }
}
