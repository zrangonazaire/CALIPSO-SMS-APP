package com.calipso.smstemplate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.importprofile.ExcelImportProfileRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sms-templates")
@RequiredArgsConstructor
public class SmsTemplateController {

    private final SmsTemplateRepository templateRepository;
    private final ExcelImportProfileRepository profileRepository;

    @PostMapping
    public SmsTemplate create(@RequestBody @Valid CreateSmsTemplateRequest request) {
        ExcelImportProfile profile = profileRepository.findById(request.profileId())
                .orElseThrow(() -> new RuntimeException("Profil introuvable"));

        SmsTemplate template = SmsTemplate.builder()
                .company(profile.getCompany())
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
}
