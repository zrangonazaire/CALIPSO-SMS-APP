package com.calipso.campaign;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.excel.ExcelImportService;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.importprofile.ExcelImportProfileRepository;
import com.calipso.recipient.CampaignRecipient;
import com.calipso.recipient.CampaignRecipientRepository;
import com.calipso.recipient.RecipientStatus;
import com.calipso.sms.MultichannelGatewayService;
import com.calipso.sms.SmsDeliveryResult;
import com.calipso.sms.SmsSendHistory;
import com.calipso.sms.SmsSendHistoryRepository;
import com.calipso.sms.SmsSendSource;
import com.calipso.smstemplate.SmsTemplate;
import com.calipso.smstemplate.SmsTemplateRepository;
import com.calipso.subscription.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignRepository campaignRepository;
    private final CompanyRepository companyRepository;
    private final ExcelImportProfileRepository profileRepository;
    private final SmsTemplateRepository templateRepository;
    private final ExcelVariableRepository variableRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final SmsSendHistoryRepository historyRepository;
    private final SubscriptionService subscriptionService;
    private final MultichannelGatewayService gatewayService;
    private final ExcelImportService excelImportService;

    @PostMapping
    public Campaign create(@RequestBody @Valid CreateCampaignRequest request) {
        ExcelImportProfile profile = request.profileId() != null && request.profileId() > 0
                ? profileRepository.findById(request.profileId()).orElse(null)
                : null;

        SmsTemplate template = request.templateId() != null && request.templateId() > 0
                ? templateRepository.findById(request.templateId()).orElse(null)
                : null;

        ExcelVariable phoneVariable = request.phoneVariableId() != null && request.phoneVariableId() > 0
                ? variableRepository.findById(request.phoneVariableId()).orElse(null)
                : null;

        Company company;
        if (request.companyId() != null && request.companyId() > 0) {
            company = companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
        } else if (profile != null) {
            company = profile.getCompany();
        } else {
            company = companyRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("Aucune entreprise disponible"));
        }

        CampaignChannel channel = request.channel() == null ? CampaignChannel.SMS : request.channel();
        String messageTemplate = request.messageTemplate();
        if ((messageTemplate == null || messageTemplate.isBlank()) && template != null) {
            messageTemplate = template.getContent();
        }

        Campaign campaign = Campaign.builder()
                .company(company)
                .profile(profile)
                .template(template)
                .phoneVariable(phoneVariable)
                .name(request.name())
                .description(request.description())
                .channel(channel)
                .messageTemplate(messageTemplate)
                .emailSubject(request.emailSubject())
                .recipientColumn(request.recipientColumn())
                .mediaUrl(request.mediaUrl())
                .ctaLabel(request.ctaLabel())
                .ctaUrl(request.ctaUrl())
                .status(CampaignStatus.DRAFT)
                .build();

        return campaignRepository.save(campaign);
    }

    @PostMapping("/analyze-file")
    public FileAnalysisResponse analyzeFile(@RequestParam("file") MultipartFile file) {
        return excelImportService.analyzeFile(file);
    }

    @PostMapping("/wizard")
    public Campaign createFromWizard(@RequestBody @Valid WizardCampaignRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        CampaignChannel channel = request.channel() == null ? CampaignChannel.SMS : request.channel();

        Campaign campaign = campaignRepository.save(Campaign.builder()
                .company(company)
                .name(request.name().trim())
                .description(request.description())
                .channel(channel)
                .messageTemplate(request.messageTemplate())
                .emailSubject(request.emailSubject())
                .recipientColumn(request.recipientColumn())
                .mediaUrl(request.mediaUrl())
                .ctaLabel(request.ctaLabel())
                .ctaUrl(request.ctaUrl())
                .status(CampaignStatus.DRAFT)
                .build());

        if (request.rows() != null && !request.rows().isEmpty()) {
            campaign = excelImportService.importRowsIntoCampaign(campaign, request.rows(), request.recipientColumn());
        }

        if (request.sendImmediately() && campaign.getTotalValid() != null && campaign.getTotalValid() > 0) {
            return send(campaign.getId());
        }

        return campaign;
    }

    @GetMapping("/sample-excel")
    public ResponseEntity<byte[]> downloadSampleExcel() {
        byte[] bytes = excelImportService.generateSampleExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-contacts-calipso.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
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
            throw new RuntimeException("Aucun destinataire valide à envoyer");
        }

        CampaignChannel channel = campaign.resolvedChannel();

        int totalUnits = validRecipients.stream()
                .mapToInt(recipient -> recipient.getSegmentCount() == null ? 1 : recipient.getSegmentCount())
                .sum();
        subscriptionService.validateSmsBalance(campaign.getCompany(), totalUnits);

        campaign.setStatus(CampaignStatus.SENDING);
        campaignRepository.save(campaign);

        int sentCount = 0;
        int failedCount = 0;
        int sentUnits = 0;

        for (CampaignRecipient recipient : validRecipients) {
            String rawTarget = channel == CampaignChannel.EMAIL && recipient.getEmail() != null && !recipient.getEmail().isBlank()
                    ? recipient.getEmail()
                    : recipient.getPhoneNumber();
            String normalizedTarget = gatewayService.normalizeTarget(channel, rawTarget);

            SmsDeliveryResult deliveryResult = gatewayService.dispatch(
                    channel,
                    recipient.getGeneratedMessage(),
                    campaign.getEmailSubject(),
                    campaign.getCompany().getSenderPhone(),
                    normalizedTarget,
                    campaign.getMediaUrl(),
                    campaign.getCtaLabel(),
                    campaign.getCtaUrl()
            );

            RecipientStatus status = deliveryResult.sent() ? RecipientStatus.SENT : RecipientStatus.FAILED;
            if (deliveryResult.sent()) {
                sentCount++;
                sentUnits += recipient.getSegmentCount() == null ? 1 : recipient.getSegmentCount();
            } else {
                failedCount++;
            }

            recipient.setPhoneNumber(normalizedTarget);
            recipient.setChannel(channel);
            recipient.setStatus(status);
            recipient.setErrorMessage(deliveryResult.errorMessage());
            recipient.setSentAt(deliveryResult.sent() ? LocalDateTime.now() : null);
            recipientRepository.save(recipient);

            historyRepository.save(SmsSendHistory.builder()
                    .company(campaign.getCompany())
                    .campaign(campaign)
                    .source(SmsSendSource.CAMPAIGN)
                    .channel(channel)
                    .phoneNumber(normalizedTarget)
                    .subject(campaign.getEmailSubject())
                    .message(recipient.getGeneratedMessage())
                    .segmentCount(recipient.getSegmentCount())
                    .status(status)
                    .errorMessage(deliveryResult.errorMessage())
                    .providerResponse(deliveryResult.providerResponse())
                    .providerResourceUrl(deliveryResult.providerResourceUrl())
                    .providerResourceId(deliveryResult.providerResourceId())
                    .deliveryCallbackData(deliveryResult.deliveryCallbackData())
                    .deliveryStatus(deliveryResult.deliveryCallbackData())
                    .sentAt(recipient.getSentAt())
                    .build());
        }

        subscriptionService.debitSms(
                campaign.getCompany(),
                sentUnits,
                "Campagne " + channel.name() + " : " + campaign.getName()
        );
        campaign.setTotalSent(sentCount);
        campaign.setTotalFailed(failedCount);
        campaign.setStatus(CampaignStatus.COMPLETED);

        return campaignRepository.save(campaign);
    }
}
