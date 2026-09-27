package de.agiehl.bgoffers.repository;

import de.agiehl.bgoffers.domain.ExternalHealthCheck;
import de.agiehl.bgoffers.domain.ExternalHealthCheckStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalHealthCheckStatusRepository
        extends JpaRepository<ExternalHealthCheckStatus, ExternalHealthCheck> {
}
