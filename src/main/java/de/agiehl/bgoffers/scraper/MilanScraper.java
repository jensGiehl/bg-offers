package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.OfferType;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Component
@ConditionalOnProperty(
        prefix = "offers.sources",
        name = "milan-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MilanScraper implements OfferScraper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MilanScraper.class);

    private final DocumentClient client;
    private final OfferProperties properties;

    public MilanScraper(DocumentClient client, OfferProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public OfferSource source() {
        return OfferSource.MILAN;
    }

    @Override
    public List<ScrapedOffer> scrape() {
        var listing = client.fetch(properties.sources().milan());
        var seeds = parseListing(listing);
        return loadDetailImages(seeds);
    }

    List<OfferSeed> parseListing(Document document) {
        var seeds = new ArrayList<OfferSeed>();
        for (var product : document.select(".productList .product")) {
            var title = product.selectFirst(".title a[alt][href]");
            var price = product.selectFirst(".productSpecialPrice");
            if (title == null || price == null) {
                continue;
            }
            var parsedPrice = MoneyParser.parse(price.text());
            if (parsedPrice.isEmpty()) {
                continue;
            }
            var delivery = product.selectFirst(".delivery");
            var thumbnail = product.selectFirst(".picture img[src]");
            seeds.add(new OfferSeed(
                    title.attr("alt").trim(),
                    title.absUrl("href"),
                    parsedPrice.orElseThrow(),
                    delivery == null ? "Keine Angabe" : delivery.text().trim(),
                    thumbnail == null ? null : thumbnail.absUrl("src")));
        }
        return List.copyOf(seeds);
    }

    String parseDetailImage(Document document) {
        var preferred = document.selectFirst(".detail__image");
        if (preferred != null) {
            var image = "img".equalsIgnoreCase(preferred.tagName()) ? preferred : preferred.selectFirst("img[src]");
            if (image != null && !image.absUrl("src").isBlank()) {
                return image.absUrl("src");
            }
            if (!preferred.absUrl("href").isBlank()) {
                return preferred.absUrl("href");
            }
        }
        var originalImageLink = document.selectFirst("a.highslide[href]");
        if (originalImageLink != null && !originalImageLink.absUrl("href").isBlank()) {
            return originalImageLink.absUrl("href");
        }
        var fallback = document.selectFirst("a.highslide img[src]");
        return fallback == null ? null : fallback.absUrl("src");
    }

    private List<ScrapedOffer> loadDetailImages(List<OfferSeed> seeds) {
        var semaphore = new Semaphore(Math.max(1, properties.http().milanConcurrency()));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = seeds.stream()
                    .map(seed -> executor.submit(() -> loadDetailImage(seed, semaphore)))
                    .toList();
            var offers = new ArrayList<ScrapedOffer>(tasks.size());
            for (Future<ScrapedOffer> task : tasks) {
                try {
                    offers.add(task.get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new SourceAccessException("Milan-Detailabrufe wurden unterbrochen", exception);
                } catch (ExecutionException exception) {
                    throw new SourceAccessException("Milan-Detailabruf ist fehlgeschlagen", exception.getCause());
                }
            }
            return List.copyOf(offers);
        }
    }

    private ScrapedOffer loadDetailImage(OfferSeed seed, Semaphore semaphore) {
        var imageUrl = seed.thumbnailUrl();
        try {
            semaphore.acquire();
            try {
                var detail = client.fetch(URI.create(seed.url()));
                var detailImage = parseDetailImage(detail);
                if (detailImage != null && !detailImage.isBlank()) {
                    imageUrl = detailImage;
                }
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Bildabruf für {} wurde unterbrochen", seed.url());
        } catch (SourceAccessException exception) {
            LOGGER.warn("Bildabruf für {} ist fehlgeschlagen: {}", seed.url(), exception.getMessage());
        }
        return new ScrapedOffer(
                source(),
                OfferType.STANDARD,
                seed.name(),
                seed.url(),
                imageUrl,
                seed.price(),
                seed.availability(),
                null,
                null);
    }

    record OfferSeed(String name, String url, java.math.BigDecimal price, String availability, String thumbnailUrl) {
    }
}
