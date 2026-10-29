package com.calipso.sms;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.calipso.statistics.StatisticsPeriod;
import java.time.LocalDateTime;

import java.util.List;

public interface SmsSendHistoryRepository extends JpaRepository<SmsSendHistory, Long> {

    List<SmsSendHistory> findByCompanyIdOrderBySentAtDesc(Long companyId);

    List<SmsSendHistory> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("select h from SmsSendHistory h where h.company.id = :companyId and coalesce(h.sentAt, h.createdAt) >= :start and coalesce(h.sentAt, h.createdAt) < :end order by coalesce(h.sentAt, h.createdAt) desc, h.id desc")
    List<SmsSendHistory> findWithinPeriod(Long companyId, LocalDateTime start, LocalDateTime end);

    @Query("select h from SmsSendHistory h where h.company.id = :companyId and coalesce(h.sentAt, h.createdAt) >= :start order by coalesce(h.sentAt, h.createdAt) desc, h.id desc")
    List<SmsSendHistory> findSince(Long companyId, LocalDateTime start);

    @Query("select h from SmsSendHistory h where h.company.id = :companyId and coalesce(h.sentAt, h.createdAt) < :end order by coalesce(h.sentAt, h.createdAt) desc, h.id desc")
    List<SmsSendHistory> findBefore(Long companyId, LocalDateTime end);

    default List<SmsSendHistory> findForPeriod(Long companyId, StatisticsPeriod period) {
        if (period.startInclusive() != null && period.endExclusive() != null) return findWithinPeriod(companyId, period.startInclusive(), period.endExclusive());
        if (period.startInclusive() != null) return findSince(companyId, period.startInclusive());
        if (period.endExclusive() != null) return findBefore(companyId, period.endExclusive());
        return findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    List<SmsSendHistory> findByProviderResourceIdOrderByCreatedAtDesc(String providerResourceId);

    List<SmsSendHistory> findByDeliveryCallbackDataOrderByCreatedAtDesc(String deliveryCallbackData);

    List<SmsSendHistory> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
