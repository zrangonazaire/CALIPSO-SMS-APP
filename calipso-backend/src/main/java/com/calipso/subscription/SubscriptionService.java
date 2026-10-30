package com.calipso.subscription;

import com.calipso.billing.TransactionType;
import com.calipso.campaign.CampaignChannel;
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
                .filter(plan -> plan.getPackagePrice() != null)
                .sorted(Comparator.comparingInt(SubscriptionPlan::getPackagePrice))
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    @Transactional
    public CompanySubscriptionSummaryResponse getCompanySubscription(Long companyId) {
        Company company = findCompany(companyId);
        CompanySubscription latest = subscriptionRepository.findFirstByCompanyIdOrderByStartedAtDesc(companyId).orElse(null);
        CompanySubscription activeSubscription = findActiveSubscription(companyId)
                .orElseGet(() -> latest != null && latest.getPlan().getPackagePrice() != null
                        ? latest : ensureStarterSubscription(company).orElse(null));
        return toSummary(company, activeSubscription, null);
    }

    @Transactional(readOnly = true)
    public PriceSimulationResponse simulatePricing(
            Long companyId,
            Integer requestedUnits,
            SubscriptionPlanCode explicitPlanCode
    ) {
        int units = (requestedUnits == null || requestedUnits <= 0) ? 100 : requestedUnits;
        List<SubscriptionPlan> activePlans = planRepository.findByActiveTrueOrderByPricePerSmsDesc().stream()
                .filter(plan -> plan.getPackagePrice() == null).toList();

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
            if (explicitPlan.getPackagePrice() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette offre multicanale s’achète à prix fixe depuis le catalogue des forfaits.");
            }
            if (units < explicitPlan.getMinRechargeUnits()) {
                units = explicitPlan.getMinRechargeUnits();
            }
            appliedPlan = explicitPlan;
            autoUpgraded = currentPlan == null || !currentPlan.getCode().equals(explicitPlan.getCode());
        } else if (currentPlan != null && currentPlan.getPackagePrice() == null) {
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
        SubscriptionPlan plan = planRepository.findByCode(request.planCode())
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formule introuvable"));
        if (plan.getPackagePrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette ancienne formule n’est plus commercialisée. Choisissez une offre de 3, 6 ou 12 mois.");
        }
        if (request.smsUnits() != null && !request.smsUnits().equals(plan.getMinRechargeUnits())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les volumes d’un forfait multicanal sont fixes.");
        }
        Company company = lockedCompany(companyId);
        PaymentMethod method = request.paymentMethod() == null ? PaymentMethod.WAVE_CI : request.paymentMethod();
        if (method == PaymentMethod.SYSTEM) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Moyen de paiement invalide.");
        String payerPhone = (request.payerPhone() == null || request.payerPhone().isBlank())
                ? company.getPhone()
                : request.payerPhone();
        var payment = paymentGatewayService.processPayment(companyId, company.getName(), plan.getPackagePrice(),
                plan.getMinRechargeUnits(), method, payerPhone, "Forfait " + plan.getName() + " · " + plan.getDurationMonths() + " mois");
        var transaction = WalletTransaction.builder().company(company).purchasedPlan(plan)
                .type(TransactionType.CREDIT).smsUnits(plan.getMinRechargeUnits()).unitPrice(0)
                .whatsappUnits(plan.getIncludedWhatsapp()).emailUnits(plan.getIncludedEmail())
                .packageDurationMonths(plan.getDurationMonths()).amount(plan.getPackagePrice())
                .balanceAfter(value(company.getSmsBalance())).reference(payment.reference())
                .paymentMethod(payment.paymentMethod()).paymentStatus(payment.status())
                .payerPhone(payment.normalizedPhone()).providerName(payment.providerName()).checkoutUrl(payment.checkoutUrl())
                .reason("Forfait " + plan.getName() + " · " + plan.getDurationMonths() + " mois · SMS, WhatsApp et e-mail").build();
        if (payment.status() == PaymentStatus.COMPLETED) applyPackage(transaction);
        transactionRepository.save(transaction);
        return toSummary(company, transaction.getSubscription() != null ? transaction.getSubscription()
                : findActiveSubscription(companyId).orElse(null), transaction);
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
        if (currentSubscription.getPlan().getPackagePrice() == null && (Boolean.TRUE.equals(simulation.autoUpgraded())
                || !currentSubscription.getPlan().getCode().equals(targetPlan.getCode()))) {
            effectiveSubscription = activatePlanSubscription(company, targetPlan);
        } else if (currentSubscription.getPlan().getPackagePrice() == null) {
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
        int newBalance = paymentResult.status() == PaymentStatus.COMPLETED ? Math.addExact(currentBalance, units) : currentBalance;
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
        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            return WalletTransactionResponse.from(transaction);
        }
        if ("ACCEPTED".equals(normalizedStatus) || "SUCCESS".equals(normalizedStatus) || "COMPLETED".equals(normalizedStatus)) {
            if (transaction.getPurchasedPlan() != null) {
                transaction.setCompany(lockedCompany(transaction.getCompany().getId()));
                applyPackage(transaction);
            } else {
                Company company = lockedCompany(transaction.getCompany().getId());
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
                .unitPrice(subscription.getPlan().getPackagePrice() == null ? subscription.getPlan().getPricePerSms() : 0)
                .amount(subscription.getPlan().getPackagePrice() == null ? smsUnits * subscription.getPlan().getPricePerSms() : 0)
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
        return activatePlanSubscription(company, plan, plan.getDurationMonths());
    }

    private CompanySubscription activatePlanSubscription(Company company, SubscriptionPlan plan, Integer months) {
        LocalDateTime now = LocalDateTime.now();
        var previous = findActiveSubscription(company.getId());
        LocalDateTime termStart = previous.filter(current -> current.getPlan().getCode() == plan.getCode())
                .map(CompanySubscription::getExpiresAt).orElse(now);
        previous.ifPresent(current -> {
            current.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(current);
        });

        return subscriptionRepository.save(CompanySubscription.builder()
                .company(company)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .expiresAt(months == null ? now.plusDays(plan.getDurationDays()) : termStart.plusMonths(months))
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
                .or(() -> {
                    var latest = subscriptionRepository.findFirstByCompanyIdOrderByStartedAtDesc(companyId);
                    if (latest.isPresent() && latest.get().getPlan().getPackagePrice() != null) return Optional.empty();
                    return ensureStarterSubscription(company);
                })
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
                lastTransaction == null ? null : WalletTransactionResponse.from(lastTransaction),
                company.getWhatsappBalance(), company.getEmailBalance()
        );
    }

    private static int value(Integer units) { return units == null ? 0 : units; }

    private Company lockedCompany(Long id) {
        return companyRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
    }

    private void applyPackage(WalletTransaction transaction) {
        Company company = transaction.getCompany();
        company.setSmsBalance(Math.addExact(value(company.getSmsBalance()), transaction.getSmsUnits()));
        company.setWhatsappBalance(Math.addExact(value(company.getWhatsappBalance()), value(transaction.getWhatsappUnits())));
        company.setEmailBalance(Math.addExact(value(company.getEmailBalance()), value(transaction.getEmailUnits())));
        companyRepository.save(company);
        transaction.setBalanceAfter(company.getSmsBalance());
        transaction.setSubscription(activatePlanSubscription(company, transaction.getPurchasedPlan(), transaction.getPackageDurationMonths()));
    }

    public int availableBalance(Company company, CampaignChannel channel) {
        return switch (channel) {
            case SMS -> value(company.getSmsBalance());
            case WHATSAPP -> company.getWhatsappBalance() == null ? value(company.getSmsBalance()) : company.getWhatsappBalance();
            case EMAIL -> company.getEmailBalance() == null ? value(company.getSmsBalance()) : company.getEmailBalance();
        };
    }

    @Transactional
    public void validateBalance(Company company, CampaignChannel channel, int units) {
        requireActiveSubscription(company.getId());
        if (units < 0 || availableBalance(company, channel) < units) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solde " + channel + " insuffisant : "
                    + availableBalance(company, channel) + " disponible(s), " + units + " requis.");
        }
    }

    @Transactional
    public void debitMessages(Company company, CampaignChannel channel, int units, String reason) {
        if (units <= 0) return;
        if (channel == CampaignChannel.SMS || channel == CampaignChannel.WHATSAPP && company.getWhatsappBalance() == null
                || channel == CampaignChannel.EMAIL && company.getEmailBalance() == null) {
            debitSms(company, units, reason);
            return;
        }
        validateBalance(company, channel, units);
        int balance = availableBalance(company, channel) - units;
        if (channel == CampaignChannel.WHATSAPP) company.setWhatsappBalance(balance);
        else company.setEmailBalance(balance);
        companyRepository.save(company);
        transactionRepository.save(WalletTransaction.builder().company(company)
                .subscription(requireActiveSubscription(company.getId())).channel(channel)
                .type(TransactionType.DEBIT).smsUnits(units).unitPrice(0).amount(0).balanceAfter(balance)
                .reference(paymentGatewayService.generateReference(PaymentMethod.SYSTEM))
                .paymentMethod(PaymentMethod.SYSTEM).paymentStatus(PaymentStatus.COMPLETED)
                .providerName("CALIPSO_ENGINE").reason(reason).build());
    }
}
