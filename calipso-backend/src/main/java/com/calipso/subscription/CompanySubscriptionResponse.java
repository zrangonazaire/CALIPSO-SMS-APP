package com.calipso.subscription;

import java.time.LocalDateTime;

public record CompanySubscriptionResponse(
        Long id,
        SubscriptionPlanCode planCode,
        String planName,
        Integer pricePerSms,
        Integer durationDays,
        SubscriptionStatus status,
        LocalDateTime startedAt,
        LocalDateTime expiresAt
) {
    public static CompanySubscriptionResponse from(CompanySubscription subscription) {
        if (subscription == null) {
            return null;
        }

        SubscriptionPlan plan = subscription.getPlan();
        return new CompanySubscriptionResponse(
                subscription.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPricePerSms(),
                plan.getDurationDays(),
                subscription.getStatus(),
                subscription.getStartedAt(),
                subscription.getExpiresAt()
        );
    }
}
