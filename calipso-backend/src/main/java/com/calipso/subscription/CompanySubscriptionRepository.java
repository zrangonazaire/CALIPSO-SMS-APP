package com.calipso.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {
    java.util.Optional<CompanySubscription> findFirstByCompanyIdOrderByStartedAtDesc(Long companyId);
    Optional<CompanySubscription> findFirstByCompanyIdAndStatusOrderByStartedAtDesc(Long companyId, SubscriptionStatus status);
}
