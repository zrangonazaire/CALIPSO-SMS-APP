package com.calipso.subscription;

import com.calipso.compagny.RechargeWalletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public List<SubscriptionPlanResponse> findPlans() {
        return subscriptionService.findPlans();
    }

    @GetMapping("/companies/{companyId}")
    public CompanySubscriptionSummaryResponse getCompanySubscription(@PathVariable Long companyId) {
        return subscriptionService.getCompanySubscription(companyId);
    }

    @GetMapping("/companies/{companyId}/simulate")
    public PriceSimulationResponse simulatePricing(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "1000") Integer smsUnits,
            @RequestParam(required = false) SubscriptionPlanCode planCode
    ) {
        return subscriptionService.simulatePricing(companyId, smsUnits, planCode);
    }

    @PostMapping("/companies/{companyId}/subscribe")
    public CompanySubscriptionSummaryResponse subscribe(
            @PathVariable Long companyId,
            @RequestBody @Valid SubscribeRequest request
    ) {
        return subscriptionService.subscribe(companyId, request);
    }

    @PostMapping("/companies/{companyId}/wallet/recharge")
    public CompanySubscriptionSummaryResponse rechargeWallet(
            @PathVariable Long companyId,
            @RequestBody @Valid RechargeWalletRequest request
    ) {
        return subscriptionService.rechargeWallet(companyId, request);
    }

    @PostMapping("/payments/webhook")
    public WalletTransactionResponse handlePaymentWebhook(@RequestBody @Valid PaymentWebhookRequest request) {
        return subscriptionService.handlePaymentWebhook(request);
    }

    @GetMapping("/companies/{companyId}/transactions")
    public List<WalletTransactionResponse> findTransactions(@PathVariable Long companyId) {
        return subscriptionService.findTransactions(companyId);
    }
}
