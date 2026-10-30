package com.calipso.subscription;

public record SubscriptionPlanResponse(
        Long id,
        SubscriptionPlanCode code,
        String name,
        Integer pricePerSms,
        Integer durationDays,
        Integer minRechargeUnits,
        Boolean active,
        String serviceTier,
        Integer durationMonths,
        Integer packagePrice,
        Integer includedSms,
        Integer includedWhatsapp,
        Integer includedEmail
) {
    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPricePerSms(),
                plan.getDurationDays(),
                plan.getMinRechargeUnits(),
                plan.getActive(), plan.getServiceTier(), plan.getDurationMonths(), plan.getPackagePrice(),
                plan.getMinRechargeUnits(), plan.getIncludedWhatsapp(), plan.getIncludedEmail()
        );
    }
}
