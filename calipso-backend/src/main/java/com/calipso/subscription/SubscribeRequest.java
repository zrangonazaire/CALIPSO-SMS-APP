package com.calipso.subscription;

import jakarta.validation.constraints.NotNull;

public record SubscribeRequest(
        @NotNull SubscriptionPlanCode planCode,
        Integer smsUnits,
        PaymentMethod paymentMethod,
        String payerPhone,
        String otpCode
) {
    public SubscribeRequest(SubscriptionPlanCode planCode) {
        this(planCode, null, PaymentMethod.WAVE_CI, null, null);
    }
}
