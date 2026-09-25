package de.agiehl.bgoffers.repository;

import de.agiehl.bgoffers.domain.ActivityLogEntry;
import de.agiehl.bgoffers.domain.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntry, Long> {

    Page<ActivityLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    long countByType(ActivityType type);
}
