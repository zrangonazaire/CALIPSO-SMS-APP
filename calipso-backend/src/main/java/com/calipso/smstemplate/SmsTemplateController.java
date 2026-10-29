package com.calipso.smstemplate;

import com.calipso.auth.CompanyAccess;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.importprofile.ExcelImportProfileRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sms-templates")
@RequiredArgsConstructor
public class SmsTemplateController {

    private final CompanyAccess access;

    private final SmsTemplateRepository templateRepository;
    private final ExcelImportProfileRepository profileRepository;
    private final CompanyRepository companyRepository;

    @PostMapping
    public SmsTemplate create(@RequestBody @Valid CreateSmsTemplateRequest request) {
        ExcelImportProfile profile = (request.profileId() != null && request.profileId() > 0)
                ? profileRepository.findById(request.profileId()).orElse(null)
                : null;

        Company company;
        if (request.companyId() != null && request.companyId() > 0) {
            company = companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
        } else if (profile != null) {
            company = profile.getCompany();
        } else {
            company = companyRepository.findById(access.currentUser().getCompany().getId())
                    .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
        }

        access.requireCompany(company);
        if (profile != null) access.requireSameCompany(company, profile.getCompany());
        SmsTemplate template = SmsTemplate.builder()
                .company(company)
                .profile(profile)
                .name(request.name())
                .content(request.content())
                .active(true)
                .build();

        return templateRepository.save(template);
    }

    @GetMapping("/profile/{profileId}")
    public List<SmsTemplate> findByProfile(@PathVariable Long profileId) {
        var profile = access.requireProfile(profileId);
        var templates = templateRepository.findByProfileIdAndActiveTrue(profileId);
        templates.forEach(template -> access.requireSameCompany(profile.getCompany(), template.getCompany()));
        return templates;
    }

    @GetMapping("/company/{companyId}")
    public List<SmsTemplate> findByCompany(@PathVariable Long companyId) {
        access.requireCompany(companyId);
        var templates = templateRepository.findByCompanyIdAndActiveTrueOrderByCreatedAtDesc(companyId);
        templates.forEach(template -> {
            if (template.getProfile() != null) access.requireSameCompany(template.getCompany(), template.getProfile().getCompany());
        });
        return templates;
    }
}
