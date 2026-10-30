package com.calipso.subscription;

public record CompanySubscriptionSummaryResponse(
        Long companyId,
        String companyName,
        Integer smsBalance,
        CompanySubscriptionResponse activeSubscription,
        WalletTransactionResponse lastTransaction,
        Integer whatsappBalance,
        Integer emailBalance
) {
    public CompanySubscriptionSummaryResponse(Long companyId, String companyName, Integer smsBalance,
            CompanySubscriptionResponse activeSubscription, WalletTransactionResponse lastTransaction) {
        this(companyId, companyName, smsBalance, activeSubscription, lastTransaction, null, null);
    }
    public CompanySubscriptionSummaryResponse(
            Long companyId,
            String companyName,
            Integer smsBalance,
            CompanySubscriptionResponse activeSubscription
    ) {
        this(companyId, companyName, smsBalance, activeSubscription, null);
    }
}
