package com.calipso.subscription;

public record CompanySubscriptionSummaryResponse(
        Long companyId,
        String companyName,
        Integer smsBalance,
        CompanySubscriptionResponse activeSubscription,
        WalletTransactionResponse lastTransaction
) {
    public CompanySubscriptionSummaryResponse(
            Long companyId,
            String companyName,
            Integer smsBalance,
            CompanySubscriptionResponse activeSubscription
    ) {
        this(companyId, companyName, smsBalance, activeSubscription, null);
    }
}
