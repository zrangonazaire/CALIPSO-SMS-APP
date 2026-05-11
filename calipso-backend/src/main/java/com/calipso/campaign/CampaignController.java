package com.calipso.campaign;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.importprofile.ExcelImportProfileRepository;
import com.calipso.recipient.CampaignRecipient;
import com.calipso.recipient.CampaignRecipientRepository;
import com.calipso.recipient.RecipientStatus;
import com.calipso.smstemplate.SmsTemplate;
import com.calipso.smstemplate.SmsTemplateRepository;
import com.calipso.sms.SmsSendHistory;
import com.calipso.sms.SmsSendHistoryRepository;
import com.calipso.sms.SmsSendSource;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignRepository campaignRepository;
    private final ExcelImportProfileRepository profileRepository;
    private final SmsTemplateRepository templateRepository;
    private final ExcelVariableRepository variableRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final SmsSendHistoryRepository historyRepository;

    @PostMapping
    public Campaign create(@RequestBody @Valid CreateCampaignRequest request) {
        ExcelImportProfile profile = profileRepository.findById(request.profileId())
                .orElseThrow(() -> new RuntimeException("Profil introuvable"));

        SmsTemplate template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new RuntimeException("Modèle SMS introuvable"));

        ExcelVariable phoneVariable = variableRepository.findById(request.phoneVariableId())
                .orElseThrow(() -> new RuntimeException("Variable téléphone introuvable"));

        if (!phoneVariable.getProfile().getId().equals(profile.getId())) {
            throw new RuntimeException("La variable destinataire SMS doit appartenir au profil de la campagne");
        }

        if (!Boolean.TRUE.equals(phoneVariable.getPhone())) {
            throw new RuntimeException("La variable choisie doit etre marquee comme destinataire SMS");
        }

        Campaign campaign = Campaign.builder()
                .company(profile.getCompany())
                .profile(profile)
                .template(template)
                .phoneVariable(phoneVariable)
                .name(request.name())
                .description(request.description())
                .status(CampaignStatus.DRAFT)
                .build();

        return campaignRepository.save(campaign);
    }

    @GetMapping("/company/{companyId}")
    public List<Campaign> findByCompany(@PathVariable Long companyId) {
        return campaignRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @PostMapping("/{campaignId}/send")
    public Campaign send(@PathVariable Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable"));

        List<CampaignRecipient> recipients = recipientRepository.findByCampaignId(campaignId);
        List<CampaignRecipient> validRecipients = recipients.stream()
                .filter(recipient -> recipient.getStatus() == RecipientStatus.VALID)
                .toList();

        if (validRecipients.isEmpty()) {
            throw new RuntimeException("Aucun destinataire valide a envoyer");
        }

        int totalSegments = validRecipients.stream()
                .mapToInt(recipient -> recipient.getSegmentCount() == null ? 1 : recipient.getSegmentCount())
                .sum();

        int balance = campaign.getCompany().getSmsBalance() == null ? 0 : campaign.getCompany().getSmsBalance();
        if (totalSegments > balance) {
            throw new RuntimeException("Solde SMS insuffisant");
        }

        campaign.setStatus(CampaignStatus.SENDING);
        campaignRepository.save(campaign);

        for (CampaignRecipient recipient : validRecipients) {
            recipient.setStatus(RecipientStatus.SENT);
            recipient.setSentAt(LocalDateTime.now());
            recipientRepository.save(recipient);

            historyRepository.save(SmsSendHistory.builder()
                    .company(campaign.getCompany())
                    .campaign(campaign)
                    .source(SmsSendSource.CAMPAIGN)
                    .phoneNumber(recipient.getPhoneNumber())
                    .message(recipient.getGeneratedMessage())
                    .segmentCount(recipient.getSegmentCount())
                    .status(RecipientStatus.SENT)
                    .sentAt(recipient.getSentAt())
                    .build());
        }

        campaign.getCompany().setSmsBalance(balance - totalSegments);
        campaign.setTotalSent(validRecipients.size());
        campaign.setTotalFailed(0);
        campaign.setStatus(CampaignStatus.COMPLETED);

        return campaignRepository.save(campaign);
    }
}
