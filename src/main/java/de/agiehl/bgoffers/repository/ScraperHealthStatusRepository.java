package de.agiehl.bgoffers.repository;

import de.agiehl.bgoffers.domain.OfferSource;
import de.agiehl.bgoffers.domain.ScraperHealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScraperHealthStatusRepository extends JpaRepository<ScraperHealthStatus, OfferSource> {
}
