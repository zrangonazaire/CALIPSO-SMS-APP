package com.calipso.subscription;

import com.calipso.auth.CompanyAccess;

import com.calipso.compagny.RechargeWalletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final CompanyAccess access;

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public List<SubscriptionPlanResponse> findPlans() {
        return subscriptionService.findPlans();
    }

    @GetMapping("/companies/{companyId}")
    public CompanySubscriptionSummaryResponse getCompanySubscription(@PathVariable Long companyId) {
        access.requireCompany(companyId);
        return subscriptionService.getCompanySubscription(companyId);
    }

    @GetMapping("/companies/{companyId}/simulate")
    public PriceSimulationResponse simulatePricing(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "1000") Integer smsUnits,
            @RequestParam(required = false) SubscriptionPlanCode planCode
    ) {
        access.requireCompany(companyId);
        return subscriptionService.simulatePricing(companyId, smsUnits, planCode);
    }

    @PostMapping("/companies/{companyId}/subscribe")
    public CompanySubscriptionSummaryResponse subscribe(
            @PathVariable Long companyId,
            @RequestBody @Valid SubscribeRequest request
    ) {
        access.requireCompany(companyId);
        return subscriptionService.subscribe(companyId, request);
    }

    @PostMapping("/companies/{companyId}/wallet/recharge")
    public CompanySubscriptionSummaryResponse rechargeWallet(
            @PathVariable Long companyId,
            @RequestBody @Valid RechargeWalletRequest request
    ) {
        access.requireCompany(companyId);
        return subscriptionService.rechargeWallet(companyId, request);
    }

    @PostMapping("/payments/webhook")
    public WalletTransactionResponse handlePaymentWebhook(@RequestBody @Valid PaymentWebhookRequest request,
            @RequestHeader(value = "X-Calipso-Webhook-Secret", required = false) String secret) {
        access.requirePaymentWebhook(secret);
        return subscriptionService.handlePaymentWebhook(request);
    }

    @GetMapping("/companies/{companyId}/transactions")
    public List<WalletTransactionResponse> findTransactions(@PathVariable Long companyId) {
        access.requireCompany(companyId);
        return subscriptionService.findTransactions(companyId);
    }
}
