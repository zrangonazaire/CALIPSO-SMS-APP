package com.calipso.subscription;

public record CompanySubscriptionSummaryResponse(
        Long companyId,
        String companyName,
        Integer smsBalance,
        CompanySubscriptionResponse activeSubscription
) {
}
