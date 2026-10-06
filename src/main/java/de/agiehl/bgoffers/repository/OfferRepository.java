package de.agiehl.bgoffers.repository;

import de.agiehl.bgoffers.domain.Offer;
import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.LookupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findTop50ByNextLookupAtLessThanEqualOrderByNextLookupAtAsc(Instant now);

    Optional<Offer> findBySourceAndSourceUrl(OfferSource source, String sourceUrl);

    Optional<Offer> findBySourceAndSourceOfferId(OfferSource source, String sourceOfferId);

    Optional<Offer> findFirstBySourceOrderByFirstSeenAtDesc(OfferSource source);

    Page<Offer> findAllByOrderByLastChangedAtDescIdDesc(Pageable pageable);

    Page<Offer> findBySourceOrderByLastChangedAtDescIdDesc(OfferSource source, Pageable pageable);

    long countBySource(OfferSource source);

    List<Offer> findByBggStatusOrderByNameAsc(LookupStatus status);

    List<Offer> findByComparisonStatusOrderByNameAsc(LookupStatus status);
}
