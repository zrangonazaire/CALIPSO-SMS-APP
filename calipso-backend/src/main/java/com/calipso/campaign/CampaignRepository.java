package com.calipso.campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;
import com.calipso.statistics.StatisticsPeriod;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Campaign> findByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Long companyId, LocalDateTime start, LocalDateTime end);
    List<Campaign> findByCompanyIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Long companyId, LocalDateTime start);
    List<Campaign> findByCompanyIdAndCreatedAtLessThanOrderByCreatedAtDesc(Long companyId, LocalDateTime end);

    default List<Campaign> findForPeriod(Long companyId, StatisticsPeriod period) {
        if (period.startInclusive() != null && period.endExclusive() != null) return findByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(companyId, period.startInclusive(), period.endExclusive());
        if (period.startInclusive() != null) return findByCompanyIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(companyId, period.startInclusive());
        if (period.endExclusive() != null) return findByCompanyIdAndCreatedAtLessThanOrderByCreatedAtDesc(companyId, period.endExclusive());
        return findByCompanyIdOrderByCreatedAtDesc(companyId);
    }
}
