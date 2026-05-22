package com.calipso.subscription;

import com.calipso.billing.TransactionType;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.sms.OrangeSmsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final CompanyRepository companyRepository;
    private final SubscriptionPlanRepository planRepository;
    private final CompanySubscriptionRepository subscriptionRepository;
    private final WalletTransactionRepository transactionRepository;
    private final OrangeSmsClient orangeSmsClient;

    public List<SubscriptionPlanResponse> findPlans() {
        return planRepository.findByActiveTrueOrderByPricePerSmsDesc()
                .stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    public CompanySubscriptionSummaryResponse getCompanySubscription(Long companyId) {
        Company company = findCompany(companyId);
        synchronizeOrangeSmsBalance(company);
        CompanySubscription activeSubscription = findActiveSubscription(companyId).orElse(null);
        return toSummary(company, activeSubscription);
    }

    @Transactional
    public CompanySubscriptionSummaryResponse subscribe(Long companyId, SubscribeRequest request) {
        Company company = findCompany(companyId);
        SubscriptionPlan plan = planRepository.findByCode(request.planCode())
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formule introuvable"));

        LocalDateTime now = LocalDateTime.now();
        findActiveSubscription(companyId).ifPresent(current -> {
            current.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(current);
        });

        CompanySubscription subscription = subscriptionRepository.save(CompanySubscription.builder()
                .company(company)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .expiresAt(now.plusDays(plan.getDurationDays()))
                .build());

        return toSummary(company, subscription);
    }

    @Transactional
    public CompanySubscriptionSummaryResponse rechargeWallet(Long companyId, Integer smsUnits) {
        if (smsUnits == null || smsUnits <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nombre de SMS doit etre positif");
        }

        Company company = findCompany(companyId);
        CompanySubscription subscription = requireActiveSubscription(companyId);
        SubscriptionPlan plan = subscription.getPlan();

        if (smsUnits < plan.getMinRechargeUnits()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recharge minimale: " + plan.getMinRechargeUnits() + " SMS");
        }

        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        company.setSmsBalance(currentBalance + smsUnits);
        companyRepository.save(company);

        transactionRepository.save(WalletTransaction.builder()
                .company(company)
                .subscription(subscription)
                .type(TransactionType.CREDIT)
                .smsUnits(smsUnits)
                .unitPrice(plan.getPricePerSms())
                .amount(smsUnits * plan.getPricePerSms())
                .reason("Recharge SMS")
                .build());

        return toSummary(company, subscription);
    }

    @Transactional
    public void debitSms(Company company, int smsUnits, String reason) {
        if (smsUnits <= 0) {
            return;
        }

        CompanySubscription subscription = requireActiveSubscription(company.getId());

        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        if (smsUnits > currentBalance) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solde SMS insuffisant");
        }

        company.setSmsBalance(currentBalance - smsUnits);
        companyRepository.save(company);

        transactionRepository.save(WalletTransaction.builder()
                .company(company)
                .subscription(subscription)
                .type(TransactionType.DEBIT)
                .smsUnits(smsUnits)
                .unitPrice(subscription.getPlan().getPricePerSms())
                .amount(smsUnits * subscription.getPlan().getPricePerSms())
                .reason(reason)
                .build());
    }

    public CompanySubscription validateSmsBalance(Company company, int smsUnits) {
        CompanySubscription subscription = requireActiveSubscription(company.getId());
        synchronizeOrangeSmsBalance(company);
        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        if (smsUnits > currentBalance) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solde SMS insuffisant");
        }
        return subscription;
    }

    @Transactional
    public Company synchronizeOrangeSmsBalance(Company company) {
        int orangeBalance = orangeSmsClient.fetchAvailableSmsUnits();
        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();

        if (orangeBalance != currentBalance) {
            log.info(
                    "[SMS Balance] Synchronisation solde Orange: companyId={}, ancienSolde={}, nouveauSolde={}",
                    company.getId(),
                    currentBalance,
                    orangeBalance
            );
            company.setSmsBalance(orangeBalance);
            return companyRepository.save(company);
        }

        return company;
    }

    public List<WalletTransactionResponse> findTransactions(Long companyId) {
        return transactionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(WalletTransactionResponse::from)
                .toList();
    }

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
    }

    private java.util.Optional<CompanySubscription> findActiveSubscription(Long companyId) {
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

    private CompanySubscription requireActiveSubscription(Long companyId) {
        return findActiveSubscription(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune souscription active"));
    }

    private CompanySubscriptionSummaryResponse toSummary(Company company, CompanySubscription activeSubscription) {
        return new CompanySubscriptionSummaryResponse(
                company.getId(),
                company.getName(),
                company.getSmsBalance() == null ? 0 : company.getSmsBalance(),
                CompanySubscriptionResponse.from(activeSubscription)
        );
    }
}
