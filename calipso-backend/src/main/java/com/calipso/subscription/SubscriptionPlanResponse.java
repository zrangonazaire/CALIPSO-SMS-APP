package com.calipso.subscription;

public record SubscriptionPlanResponse(
        Long id,
        SubscriptionPlanCode code,
        String name,
        Integer pricePerSms,
        Integer durationDays,
        Integer minRechargeUnits,
        Boolean active
) {
    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPricePerSms(),
                plan.getDurationDays(),
                plan.getMinRechargeUnits(),
                plan.getActive()
        );
    }
}
