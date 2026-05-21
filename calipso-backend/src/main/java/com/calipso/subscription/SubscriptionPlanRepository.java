package com.calipso.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findByActiveTrueOrderByPricePerSmsDesc();
    Optional<SubscriptionPlan> findByCode(SubscriptionPlanCode code);
}
