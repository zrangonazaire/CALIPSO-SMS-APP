package com.calipso.subscription;

public record PriceSimulationResponse(
        Integer smsUnits,
        SubscriptionPlanCode appliedPlanCode,
        String appliedPlanName,
        Integer unitPrice,
        Integer totalAmount,
        Integer baseAmount,
        Integer savingsAmount,
        Integer durationDays,
        Boolean autoUpgraded
) {
}
