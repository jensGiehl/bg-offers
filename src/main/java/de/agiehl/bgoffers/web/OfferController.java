package de.agiehl.bgoffers.web;

import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.ActivityType;
import de.agiehl.bgoffers.domain.LookupStatus;
import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.enrichment.GameNameNormalizer;
import de.agiehl.bgoffers.repository.ActivityLogRepository;
import de.agiehl.bgoffers.repository.OfferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.TreeSet;

@Controller
public class OfferController {

    private final OfferRepository repository;
    private final ActivityLogRepository activityLogRepository;
    private final GameNameNormalizer normalizer;

    public OfferController(
            OfferRepository repository,
            ActivityLogRepository activityLogRepository,
            GameNameNormalizer normalizer) {
        this.repository = repository;
        this.activityLogRepository = activityLogRepository;
        this.normalizer = normalizer;
    }

    @GetMapping("/aktivitaeten")
    public String activities(@RequestParam(defaultValue = "0") int page, Model model) {
        var pageRequest = PageRequest.of(Math.max(page, 0), 30);
        var activities = activityLogRepository.findAllByOrderByOccurredAtDesc(pageRequest);
        var sent = activityLogRepository.countByType(ActivityType.TELEGRAM_SENT);
        var failed = activityLogRepository.countByType(ActivityType.TELEGRAM_FAILED);
        var retries = activityLogRepository.countByType(ActivityType.HTTP_RETRY)
                + activityLogRepository.countByType(ActivityType.LOOKUP_RETRY);
        model.addAttribute("activities", activities);
        model.addAttribute("total", activityLogRepository.count());
        model.addAttribute("offerEvents",
                activityLogRepository.countByType(ActivityType.OFFER_FOUND)
                        + activityLogRepository.countByType(ActivityType.PRICE_CHANGED));
        model.addAttribute("telegramEvents", sent + failed);
        model.addAttribute("retryEvents", retries);
        return "activities";
    }

    @GetMapping("/fehlende-treffer")
    public String missingLookups(Model model) {
        var bggTerms = missingTerms(repository.findByBggStatusOrderByNameAsc(LookupStatus.NOT_FOUND));
        var comparisonTerms = missingTerms(
                repository.findByComparisonStatusOrderByNameAsc(LookupStatus.NOT_FOUND));
        model.addAttribute("bggTerms", bggTerms);
        model.addAttribute("comparisonTerms", comparisonTerms);
        return "missing-lookups";
    }

    @GetMapping("/")
    public String index(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) OfferSource source,
            Model model) {
        var pageRequest = PageRequest.of(Math.max(page, 0), 24);
        var offers = source == null
                ? repository.findAllByOrderByLastSeenAtDesc(pageRequest)
                : repository.findBySourceOrderByLastSeenAtDesc(source, pageRequest);
        model.addAttribute("offers", offers);
        model.addAttribute("selectedSource", source);
        model.addAttribute("sources", OfferSource.values());
        model.addAttribute("total", repository.count());
        model.addAttribute("spieleOffensiveCount", repository.countBySource(OfferSource.SPIELE_OFFENSIVE));
        model.addAttribute("milanCount", repository.countBySource(OfferSource.MILAN));
        model.addAttribute("bggMarketCount", repository.countBySource(OfferSource.BGG_MARKET));
        model.addAttribute("unknownsCount", repository.countBySource(OfferSource.UNKNOWNS));
        return "index";
    }

    @GetMapping("/angebote/{id}")
    public String detail(@PathVariable long id, Model model) {
        var offer = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("offer", offer);
        return "offer";
    }

    private List<String> missingTerms(List<Offer> offers) {
        var terms = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        offers.stream()
                .map(Offer::getName)
                .map(normalizer::searchTerm)
                .filter(term -> !term.isBlank())
                .forEach(terms::add);
        return List.copyOf(terms);
    }
}
