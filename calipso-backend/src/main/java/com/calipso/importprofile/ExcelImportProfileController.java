package com.calipso.importprofile;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/import-profiles")
@RequiredArgsConstructor
public class ExcelImportProfileController {

    private final ExcelImportProfileRepository profileRepository;
    private final CompanyRepository companyRepository;

    @PostMapping
    public ExcelImportProfile create(@RequestBody @Valid CreateImportProfileRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        ExcelImportProfile profile = ExcelImportProfile.builder()
                .company(company)
                .name(request.name())
                .description(request.description())
                .active(true)
                .build();

        return profileRepository.save(profile);
    }

    @GetMapping("/company/{companyId}")
    public List<ExcelImportProfile> findByCompany(@PathVariable Long companyId) {
        return profileRepository.findByCompanyIdAndActiveTrue(companyId);
    }
}