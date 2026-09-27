package de.agiehl.bgoffers.scraper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import de.agiehl.bgoffers.config.OfferProperties;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

@Component("priceComparisonDocumentClient")
public class BrowserDocumentClient implements DocumentClient, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserDocumentClient.class);
    private static final String JSON_FETCH_SCRIPT = """
            async url => {
              const response = await fetch(url, {
                credentials: 'include',
                headers: {
                  'Accept': 'application/json,text/javascript,*/*;q=0.1',
                  'X-Requested-With': 'XMLHttpRequest'
                }
              });
              return { status: response.status, body: await response.text() };
            }
            """;

    private final OfferProperties properties;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private long lastRequestStartedAt;

    public BrowserDocumentClient(OfferProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized Document fetch(URI uri) {
        requirePriceComparisonOrigin(uri);
        try {
            var activePage = page();
            waitForRequestSlot();
            activePage.navigate(uri.toString(), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(timeoutMillis()));
            waitForChallenge(uri, activePage);
            return Jsoup.parse(activePage.content(), activePage.url());
        } catch (SourceAccessException exception) {
            resetSession();
            throw exception;
        } catch (PlaywrightException exception) {
            resetSession();
            throw new SourceAccessException(
                    "Browser-Abruf von %s ist fehlgeschlagen".formatted(uri), exception);
        }
    }

    @Override
    public synchronized String fetchJson(URI uri) {
        requirePriceComparisonOrigin(uri);
        try {
            var activePage = page();
            ensurePriceComparisonPage(activePage);
            waitForRequestSlot();
            var result = jsonResult(activePage.evaluate(JSON_FETCH_SCRIPT, uri.toString()));
            if (result.status() < 200 || result.status() >= 300) {
                throw new SourceAccessException("Browser-Abruf von %s lieferte HTTP %d"
                        .formatted(uri, result.status()));
            }
            return result.body();
        } catch (SourceAccessException exception) {
            resetSession();
            throw exception;
        } catch (PlaywrightException exception) {
            resetSession();
            throw new SourceAccessException(
                    "Browser-Abruf von %s ist fehlgeschlagen".formatted(uri), exception);
        }
    }

    private Page page() {
        if (page != null && !page.isClosed()) {
            return page;
        }
        LOGGER.info("Starte Chromium für den Preisvergleich (headless={})",
                properties.priceComparisonBrowser().headless());
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(properties.priceComparisonBrowser().headless())
                .setTimeout(timeoutMillis()));
        context = browser.newContext(new Browser.NewContextOptions()
                .setLocale("de-DE")
                .setTimezoneId("Europe/Berlin")
                .setViewportSize(1365, 768));
        page = context.newPage();
        page.setDefaultTimeout(timeoutMillis());
        page.setDefaultNavigationTimeout(timeoutMillis());
        return page;
    }

    private void ensurePriceComparisonPage(Page activePage) {
        var baseUri = properties.sources().priceComparison();
        if (!sameOrigin(baseUri, URI.create(activePage.url()))) {
            fetch(baseUri);
        }
    }

    private void waitForChallenge(URI uri, Page activePage) {
        var timeout = properties.priceComparisonBrowser().timeout();
        var deadline = System.nanoTime() + timeout.toNanos();
        while (isTemporaryChallenge(activePage) && System.nanoTime() < deadline) {
            activePage.waitForTimeout(Math.min(500, timeout.toMillis()));
        }
        if (isBlocked(activePage)) {
            throw new SourceAccessException(
                    "%s hat den Browser-Abruf mit einer Zugriffsprüfung abgewiesen".formatted(uri));
        }
    }

    private boolean isTemporaryChallenge(Page activePage) {
        var content = pageDescription(activePage);
        return content.contains("establishing a secure connection")
                || content.contains("hold tight")
                || content.contains("just a moment")
                || content.contains("enable javascript and cookies to continue");
    }

    private boolean isBlocked(Page activePage) {
        var content = pageDescription(activePage);
        return isTemporaryChallenge(activePage)
                || content.contains("403 forbidden")
                || content.contains("verify you are human");
    }

    private String pageDescription(Page activePage) {
        var title = activePage.title();
        var body = activePage.locator("body").count() == 0
                ? ""
                : activePage.locator("body").innerText();
        return (title + " " + body).toLowerCase();
    }

    private void requirePriceComparisonOrigin(URI uri) {
        if (!sameOrigin(properties.sources().priceComparison(), uri)) {
            throw new IllegalArgumentException(
                    "Browser-Client darf nur den konfigurierten Preisvergleich aufrufen");
        }
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private long timeoutMillis() {
        return Math.max(1, properties.priceComparisonBrowser().timeout().toMillis());
    }

    private void waitForRequestSlot() {
        var minimumDelay = properties.priceComparisonBrowser().minimumDelay();
        var remainingNanos = minimumDelay.toNanos() - (System.nanoTime() - lastRequestStartedAt);
        if (remainingNanos > 0) {
            try {
                Thread.sleep(java.time.Duration.ofNanos(remainingNanos));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SourceAccessException("Browser-Abruf wurde unterbrochen", exception);
            }
        }
        lastRequestStartedAt = System.nanoTime();
    }

    private JsonResult jsonResult(Object value) {
        if (!(value instanceof Map<?, ?> map)
                || !(map.get("status") instanceof Number status)
                || !(map.get("body") instanceof String body)) {
            throw new SourceAccessException("Browser-Suche lieferte keine auswertbare Antwort");
        }
        return new JsonResult(status.intValue(), body);
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        resetSession();
    }

    private void resetSession() {
        closeQuietly(page);
        closeQuietly(context);
        closeQuietly(browser);
        closeQuietly(playwright);
        page = null;
        context = null;
        browser = null;
        playwright = null;
        lastRequestStartedAt = 0;
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }

    private record JsonResult(int status, String body) {
    }
}
