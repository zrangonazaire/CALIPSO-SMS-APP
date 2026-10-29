package com.calipso.compagny;

import com.calipso.subscription.PaymentMethod;
import com.calipso.subscription.SubscriptionPlanCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RechargeWalletRequest(
        @NotNull @Min(1) Integer smsUnits,
        SubscriptionPlanCode planCode,
        PaymentMethod paymentMethod,
        String payerPhone,
        String otpCode
) {
    public RechargeWalletRequest(Integer smsUnits) {
        this(smsUnits, null, PaymentMethod.WAVE_CI, null, null);
    }
}
