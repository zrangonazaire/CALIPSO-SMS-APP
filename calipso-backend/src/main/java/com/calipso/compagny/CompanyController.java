package com.calipso.compagny;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyRepository companyRepository;

    @PostMapping
    public Company create(@RequestBody @Valid CreateCompanyRequest request) {
        Company company = Company.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .address(request.address())
                .contactName(request.contactName())
                .businessType(request.businessType())
                .smsBalance(0)
                .active(true)
                .build();

        return companyRepository.save(company);
    }

    @GetMapping
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @GetMapping("/{id}")
    public Company findById(@PathVariable Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
    }

    @PostMapping("/{id}/wallet/recharge")
    public Company rechargeWallet(
            @PathVariable Long id,
            @RequestBody @Valid RechargeWalletRequest request
    ) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        company.setSmsBalance(currentBalance + request.smsUnits());

        return companyRepository.save(company);
    }
}
