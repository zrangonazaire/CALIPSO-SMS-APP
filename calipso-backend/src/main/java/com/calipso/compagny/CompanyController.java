package com.calipso.compagny;

import com.calipso.auth.CompanyAccess;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.calipso.subscription.CompanySubscriptionSummaryResponse;
import com.calipso.subscription.SubscriptionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyAccess access;

    private final CompanyRepository companyRepository;
    private final SubscriptionService subscriptionService;

    @PostMapping
    public Company create(@RequestBody @Valid CreateCompanyRequest request) {
        access.requirePlatformAdmin();
        Company company = Company.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .senderPhone(request.senderPhone())
                .address(request.address())
                .contactName(request.contactName())
                .businessType(request.businessType())
                .smsBalance(50)
                .active(true)
                .build();

        return companyRepository.save(company);
    }

    @GetMapping
    public List<Company> findAll() {
        var user = access.currentUser();
        return access.isPlatformAdmin(user) ? companyRepository.findAll() : List.of(user.getCompany());
    }

    @GetMapping("/{id}")
    public Company findById(@PathVariable Long id) {
        access.requireCompany(id);
        return companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
    }

    @PutMapping("/{id}")
    public Company update(
            @PathVariable Long id,
            @RequestBody @Valid CreateCompanyRequest request
    ) {
        access.requireTeamManager(id);
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        company.setName(request.name());
        company.setEmail(request.email());
        company.setPhone(request.phone());
        company.setSenderPhone(request.senderPhone());
        company.setAddress(request.address());
        company.setContactName(request.contactName());
        company.setBusinessType(request.businessType());

        return companyRepository.save(company);
    }

    @PostMapping("/{id}/wallet/recharge")
    public CompanySubscriptionSummaryResponse rechargeWallet(
            @PathVariable Long id,
            @RequestBody @Valid RechargeWalletRequest request
    ) {
        access.requireCompany(id);
        return subscriptionService.rechargeWallet(id, request);
    }
}
