package com.calipso.subscription;

import com.calipso.billing.TransactionType;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.compagny.RechargeWalletRequest;
import com.calipso.sms.OrangeSmsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private static final int BASE_ESSENTIEL_PRICE = 20;

    private final CompanyRepository companyRepository;
    private final SubscriptionPlanRepository planRepository;
    private final CompanySubscriptionRepository subscriptionRepository;
    private final WalletTransactionRepository transactionRepository;
    private final OrangeSmsClient orangeSmsClient;
    private final MobilePaymentGatewayService paymentGatewayService;

    public List<SubscriptionPlanResponse> findPlans() {
        return planRepository.findByActiveTrueOrderByPricePerSmsDesc()
                .stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    @Transactional
    public CompanySubscriptionSummaryResponse getCompanySubscription(Long companyId) {
        Company company = findCompany(companyId);
        CompanySubscription activeSubscription = findActiveSubscription(companyId)
                .orElseGet(() -> ensureStarterSubscription(company).orElse(null));
        return toSummary(company, activeSubscription, null);
    }

    @Transactional(readOnly = true)
    public PriceSimulationResponse simulatePricing(
            Long companyId,
            Integer requestedUnits,
            SubscriptionPlanCode explicitPlanCode
    ) {
        int units = (requestedUnits == null || requestedUnits <= 0) ? 100 : requestedUnits;
        List<SubscriptionPlan> activePlans = planRepository.findByActiveTrueOrderByPricePerSmsDesc();

        SubscriptionPlan currentPlan = companyId != null
                ? subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, SubscriptionStatus.ACTIVE)
                        .filter(sub -> sub.getExpiresAt().isAfter(LocalDateTime.now()))
                        .map(CompanySubscription::getPlan)
                        .orElse(null)
                : null;

        SubscriptionPlan volumeTierPlan = resolvePlanByVolume(units, activePlans);
        SubscriptionPlan appliedPlan = volumeTierPlan;
        boolean autoUpgraded = false;

        if (explicitPlanCode != null) {
            SubscriptionPlan explicitPlan = planRepository.findByCode(explicitPlanCode)
                    .filter(p -> Boolean.TRUE.equals(p.getActive()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formule introuvable"));
            if (units < explicitPlan.getMinRechargeUnits()) {
                units = explicitPlan.getMinRechargeUnits();
            }
            appliedPlan = explicitPlan;
            autoUpgraded = currentPlan == null || !currentPlan.getCode().equals(explicitPlan.getCode());
        } else if (currentPlan != null) {
            if (volumeTierPlan.getPricePerSms() < currentPlan.getPricePerSms()) {
                appliedPlan = volumeTierPlan;
                autoUpgraded = true;
            } else {
                appliedPlan = currentPlan;
            }
        } else {
            autoUpgraded = volumeTierPlan.getCode() != SubscriptionPlanCode.ESSENTIEL;
        }

        int unitPrice = appliedPlan.getPricePerSms();
        int totalAmount = units * unitPrice;
        int baseAmount = units * BASE_ESSENTIEL_PRICE;
        int savingsAmount = Math.max(0, baseAmount - totalAmount);

        return new PriceSimulationResponse(
                units,
                appliedPlan.getCode(),
                appliedPlan.getName(),
                unitPrice,
                totalAmount,
                baseAmount,
                savingsAmount,
                appliedPlan.getDurationDays(),
                autoUpgraded
        );
    }

    @Transactional
    public CompanySubscriptionSummaryResponse subscribe(Long companyId, SubscribeRequest request) {
        Company company = findCompany(companyId);
        SubscriptionPlan plan = planRepository.findByCode(request.planCode())
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formule introuvable"));

        int minUnits = plan.getMinRechargeUnits() == null ? 100 : plan.getMinRechargeUnits();
        int unitsToBuy = request.smsUnits() == null ? minUnits : request.smsUnits();

        if (unitsToBuy < minUnits) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La formule " + plan.getName() + " nécessite un achat minimum de " + minUnits + " crédits ("
                            + (minUnits * plan.getPricePerSms()) + " FCFA)."
            );
        }

        int amountFcfa = unitsToBuy * plan.getPricePerSms();
        PaymentMethod method = request.paymentMethod() == null ? PaymentMethod.WAVE_CI : request.paymentMethod();
        String payerPhone = (request.payerPhone() == null || request.payerPhone().isBlank())
                ? company.getPhone()
                : request.payerPhone();

        MobilePaymentGatewayService.PaymentChargeResult paymentResult = paymentGatewayService.processPayment(
                company.getId(),
                company.getName(),
                amountFcfa,
                unitsToBuy,
                method,
                payerPhone,
                "Souscription Pack " + plan.getName() + " (" + unitsToBuy + " crédits)"
        );

        CompanySubscription subscription = activatePlanSubscription(company, plan);

        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        int newBalance = currentBalance + unitsToBuy;
        company.setSmsBalance(newBalance);
        companyRepository.save(company);

        WalletTransaction transaction = transactionRepository.save(WalletTransaction.builder()
                .company(company)
                .subscription(subscription)
                .type(TransactionType.CREDIT)
                .smsUnits(unitsToBuy)
                .unitPrice(plan.getPricePerSms())
                .amount(amountFcfa)
                .balanceAfter(newBalance)
                .reference(paymentResult.reference())
                .paymentMethod(paymentResult.paymentMethod())
                .paymentStatus(paymentResult.status())
                .payerPhone(paymentResult.normalizedPhone())
                .providerName(paymentResult.providerName())
                .checkoutUrl(paymentResult.checkoutUrl())
                .reason("Pack " + plan.getName() + " · " + paymentResult.paymentMethod().getLabel())
                .build());

        return toSummary(company, subscription, transaction);
    }

    @Transactional
    public CompanySubscriptionSummaryResponse rechargeWallet(Long companyId, Integer smsUnits) {
        return rechargeWallet(companyId, new RechargeWalletRequest(smsUnits, null, PaymentMethod.WAVE_CI, null, null));
    }

    @Transactional
    public CompanySubscriptionSummaryResponse rechargeWallet(Long companyId, RechargeWalletRequest request) {
        if (request == null || request.smsUnits() == null || request.smsUnits() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nombre de crédits doit être positif");
        }

        Company company = findCompany(companyId);
        CompanySubscription currentSubscription = requireActiveSubscription(companyId);

        PriceSimulationResponse simulation = simulatePricing(
                companyId,
                request.smsUnits(),
                request.planCode()
        );

        SubscriptionPlan targetPlan = planRepository.findByCode(simulation.appliedPlanCode())
                .orElse(currentSubscription.getPlan());

        CompanySubscription effectiveSubscription = currentSubscription;
        if (Boolean.TRUE.equals(simulation.autoUpgraded())
                || !currentSubscription.getPlan().getCode().equals(targetPlan.getCode())) {
            effectiveSubscription = activatePlanSubscription(company, targetPlan);
        } else {
            // Prolonge la date d'expiration si une recharge est effectuée sur la formule active
            LocalDateTime minExpiry = LocalDateTime.now().plusDays(targetPlan.getDurationDays());
            if (effectiveSubscription.getExpiresAt().isBefore(minExpiry)) {
                effectiveSubscription.setExpiresAt(minExpiry);
                effectiveSubscription = subscriptionRepository.save(effectiveSubscription);
            }
        }

        int units = simulation.smsUnits();
        int amountFcfa = simulation.totalAmount();
        PaymentMethod method = request.paymentMethod() == null ? PaymentMethod.WAVE_CI : request.paymentMethod();
        String payerPhone = (request.payerPhone() == null || request.payerPhone().isBlank())
                ? company.getPhone()
                : request.payerPhone();

        MobilePaymentGatewayService.PaymentChargeResult paymentResult = paymentGatewayService.processPayment(
                company.getId(),
                company.getName(),
                amountFcfa,
                units,
                method,
                payerPhone,
                "Recharge " + units + " crédits (" + targetPlan.getName() + ")"
        );

        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        int newBalance = currentBalance + units;
        company.setSmsBalance(newBalance);
        companyRepository.save(company);

        String reasonPrefix = Boolean.TRUE.equals(simulation.autoUpgraded())
                ? "Recharge + Surclassement " + targetPlan.getName()
                : "Recharge crédits (" + targetPlan.getName() + ")";

        WalletTransaction transaction = transactionRepository.save(WalletTransaction.builder()
                .company(company)
                .subscription(effectiveSubscription)
                .type(TransactionType.CREDIT)
                .smsUnits(units)
                .unitPrice(targetPlan.getPricePerSms())
                .amount(amountFcfa)
                .balanceAfter(newBalance)
                .reference(paymentResult.reference())
                .paymentMethod(paymentResult.paymentMethod())
                .paymentStatus(paymentResult.status())
                .payerPhone(paymentResult.normalizedPhone())
                .providerName(paymentResult.providerName())
                .checkoutUrl(paymentResult.checkoutUrl())
                .reason(reasonPrefix + " · " + paymentResult.paymentMethod().getLabel())
                .build());

        return toSummary(company, effectiveSubscription, transaction);
    }

    @Transactional
    public WalletTransactionResponse handlePaymentWebhook(PaymentWebhookRequest webhook) {
        WalletTransaction transaction = transactionRepository.findByReference(webhook.reference().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable : " + webhook.reference()));

        String normalizedStatus = webhook.status() == null ? "COMPLETED" : webhook.status().trim().toUpperCase();
        if ("ACCEPTED".equals(normalizedStatus) || "SUCCESS".equals(normalizedStatus) || "COMPLETED".equals(normalizedStatus)) {
            if (transaction.getPaymentStatus() == PaymentStatus.PENDING) {
                Company company = transaction.getCompany();
                int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
                int newBalance = currentBalance + transaction.getSmsUnits();
                company.setSmsBalance(newBalance);
                companyRepository.save(company);
                transaction.setBalanceAfter(newBalance);
            }
            transaction.setPaymentStatus(PaymentStatus.COMPLETED);
        } else if ("FAILED".equals(normalizedStatus) || "CANCELLED".equals(normalizedStatus) || "REFUSED".equals(normalizedStatus)) {
            transaction.setPaymentStatus(PaymentStatus.FAILED);
        }

        if (webhook.payerPhone() != null && !webhook.payerPhone().isBlank()) {
            transaction.setPayerPhone(webhook.payerPhone().trim());
        }

        return WalletTransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void debitSms(Company company, int smsUnits, String reason) {
        if (smsUnits <= 0) {
            return;
        }

        CompanySubscription subscription = requireActiveSubscription(company.getId());

        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        if (smsUnits > currentBalance) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solde de crédits insuffisant");
        }

        int newBalance = currentBalance - smsUnits;
        company.setSmsBalance(newBalance);
        companyRepository.save(company);

        transactionRepository.save(WalletTransaction.builder()
                .company(company)
                .subscription(subscription)
                .type(TransactionType.DEBIT)
                .smsUnits(smsUnits)
                .unitPrice(subscription.getPlan().getPricePerSms())
                .amount(smsUnits * subscription.getPlan().getPricePerSms())
                .balanceAfter(newBalance)
                .reference(paymentGatewayService.generateReference(PaymentMethod.SYSTEM))
                .paymentMethod(PaymentMethod.SYSTEM)
                .paymentStatus(PaymentStatus.COMPLETED)
                .providerName("CALIPSO_ENGINE")
                .reason(reason)
                .build());
    }

    @Transactional
    public void refundSms(Company company, int smsUnits, String reason) {
        if (smsUnits <= 0) {
            return;
        }

        CompanySubscription subscription = requireActiveSubscription(company.getId());
        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        int newBalance = currentBalance + smsUnits;
        company.setSmsBalance(newBalance);
        companyRepository.save(company);

        transactionRepository.save(WalletTransaction.builder()
                .company(company)
                .subscription(subscription)
                .type(TransactionType.REFUND)
                .smsUnits(smsUnits)
                .unitPrice(subscription.getPlan().getPricePerSms())
                .amount(smsUnits * subscription.getPlan().getPricePerSms())
                .balanceAfter(newBalance)
                .reference(paymentGatewayService.generateReference(PaymentMethod.SYSTEM))
                .paymentMethod(PaymentMethod.SYSTEM)
                .paymentStatus(PaymentStatus.REFUNDED)
                .providerName("CALIPSO_ENGINE")
                .reason(reason)
                .build());
    }

    @Transactional
    public CompanySubscription validateSmsBalance(Company company, int smsUnits) {
        CompanySubscription subscription = requireActiveSubscription(company.getId());
        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        if (smsUnits > currentBalance) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solde insuffisant (" + currentBalance + " crédits disponibles pour " + smsUnits + " requis). Rechargez votre compte."
            );
        }
        return subscription;
    }

    @Transactional
    public Company synchronizeOrangeSmsBalance(Company company) {
        try {
            int orangeBalance = orangeSmsClient.fetchAvailableSmsUnits();
            int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();

            if (orangeBalance > 0 && currentBalance == 0) {
                log.info(
                        "[SMS Balance] Initialisation solde Orange: companyId={}, nouveauSolde={}",
                        company.getId(),
                        orangeBalance
                );
                company.setSmsBalance(orangeBalance);
                return companyRepository.save(company);
            }
        } catch (Exception e) {
            log.debug("[SMS Balance] Synchronisation Orange ignorée (mode local/hors-ligne): {}", e.getMessage());
        }

        return company;
    }

    public List<WalletTransactionResponse> findTransactions(Long companyId) {
        return transactionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(WalletTransactionResponse::from)
                .toList();
    }

    private SubscriptionPlan resolvePlanByVolume(int units, List<SubscriptionPlan> plans) {
        return plans.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .filter(p -> units >= (p.getMinRechargeUnits() == null ? 1 : p.getMinRechargeUnits()))
                .min(Comparator.comparingInt(SubscriptionPlan::getPricePerSms))
                .orElseGet(() -> plans.stream()
                        .max(Comparator.comparingInt(SubscriptionPlan::getPricePerSms))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Aucune formule configurée")));
    }

    private CompanySubscription activatePlanSubscription(Company company, SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        findActiveSubscription(company.getId()).ifPresent(current -> {
            current.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(current);
        });

        return subscriptionRepository.save(CompanySubscription.builder()
                .company(company)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .expiresAt(now.plusDays(plan.getDurationDays()))
                .build());
    }

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
    }

    private Optional<CompanySubscription> findActiveSubscription(Long companyId) {
        return subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, SubscriptionStatus.ACTIVE)
                .filter(subscription -> {
                    if (subscription.getExpiresAt().isAfter(LocalDateTime.now())) {
                        return true;
                    }
                    subscription.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(subscription);
                    return false;
                });
    }

    private Optional<CompanySubscription> ensureStarterSubscription(Company company) {
        return planRepository.findByCode(SubscriptionPlanCode.ESSENTIEL)
                .or(() -> planRepository.findByActiveTrueOrderByPricePerSmsDesc().stream().findFirst())
                .map(plan -> {
                    LocalDateTime now = LocalDateTime.now();
                    return subscriptionRepository.save(CompanySubscription.builder()
                            .company(company)
                            .plan(plan)
                            .status(SubscriptionStatus.ACTIVE)
                            .startedAt(now)
                            .expiresAt(now.plusDays(plan.getDurationDays()))
                            .build());
                });
    }

    private CompanySubscription requireActiveSubscription(Long companyId) {
        Company company = findCompany(companyId);
        return findActiveSubscription(companyId)
                .or(() -> ensureStarterSubscription(company))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune souscription active"));
    }

    private CompanySubscriptionSummaryResponse toSummary(
            Company company,
            CompanySubscription activeSubscription,
            WalletTransaction lastTransaction
    ) {
        return new CompanySubscriptionSummaryResponse(
                company.getId(),
                company.getName(),
                company.getSmsBalance() == null ? 0 : company.getSmsBalance(),
                CompanySubscriptionResponse.from(activeSubscription),
                lastTransaction == null ? null : WalletTransactionResponse.from(lastTransaction)
        );
    }
}
