package com.calipso.smstemplate;

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
            company = companyRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
        }

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
        return templateRepository.findByProfileIdAndActiveTrue(profileId);
    }

    @GetMapping("/company/{companyId}")
    public List<SmsTemplate> findByCompany(@PathVariable Long companyId) {
        return templateRepository.findByCompanyIdAndActiveTrueOrderByCreatedAtDesc(companyId);
    }
}
