package com.calipso.sms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SmsSendHistoryRepository extends JpaRepository<SmsSendHistory, Long> {

    List<SmsSendHistory> findByCompanyIdOrderBySentAtDesc(Long companyId);

    List<SmsSendHistory> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<SmsSendHistory> findByProviderResourceIdOrderByCreatedAtDesc(String providerResourceId);
}
