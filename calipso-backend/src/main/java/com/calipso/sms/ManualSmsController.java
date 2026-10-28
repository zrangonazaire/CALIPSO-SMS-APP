package com.calipso.sms;

import com.calipso.auth.CompanyAccess;

import com.calipso.campaign.CampaignChannel;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.recipient.RecipientStatus;
import com.calipso.subscription.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manual-sms")
@RequiredArgsConstructor
@Slf4j
public class ManualSmsController {

    private final CompanyAccess access;

    private final CompanyRepository companyRepository;
    private final SmsMessageService smsMessageService;
    private final SmsSendHistoryRepository historyRepository;
    private final SubscriptionService subscriptionService;
    private final MultichannelGatewayService gatewayService;

    @PostMapping("/send")
    public ManualSmsResponse send(@RequestBody @Valid ManualSmsRequest request) {
        access.requireCompany(request.companyId());
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        CampaignChannel channel = request.channel() == null ? CampaignChannel.SMS : request.channel();

        List<String> targets = request.phoneNumbers() == null ? List.of() : request.phoneNumbers();
        List<String> validTargets = targets.stream()
                .filter(target -> target != null && !target.isBlank())
                .toList();

        int unitsPerRecipient = gatewayService.calculateUnits(channel, request.message(), smsMessageService);
        int totalUnits = validTargets.size() * unitsPerRecipient;

        subscriptionService.validateSmsBalance(company, totalUnits);

        int sentCount = 0;
        String firstErrorMessage = null;
        for (String rawTarget : validTargets) {
            String normalizedTarget = gatewayService.normalizeTarget(channel, rawTarget);

            SmsDeliveryResult deliveryResult = gatewayService.dispatch(
                    channel,
                    request.message(),
                    request.subject(),
                    company.getSenderPhone(),
                    normalizedTarget,
                    request.mediaUrl(),
                    request.ctaLabel(),
                    request.ctaUrl()
            );

            if (deliveryResult.sent()) {
                sentCount++;
            } else if (firstErrorMessage == null) {
                firstErrorMessage = deliveryResult.errorMessage();
            }

            historyRepository.save(SmsSendHistory.builder()
                    .company(company)
                    .source(SmsSendSource.MANUAL)
                    .channel(channel)
                    .phoneNumber(normalizedTarget)
                    .subject(request.subject())
                    .message(request.message())
                    .segmentCount(unitsPerRecipient)
                    .status(deliveryResult.sent() ? RecipientStatus.SENT : RecipientStatus.FAILED)
                    .errorMessage(deliveryResult.errorMessage())
                    .providerResponse(deliveryResult.providerResponse())
                    .providerResourceUrl(deliveryResult.providerResourceUrl())
                    .providerResourceId(deliveryResult.providerResourceId())
                    .deliveryCallbackData(deliveryResult.deliveryCallbackData())
                    .deliveryStatus(deliveryResult.deliveryCallbackData())
                    .sentAt(deliveryResult.sent() ? java.time.LocalDateTime.now() : null)
                    .build());
        }

        int sentUnits = sentCount * unitsPerRecipient;
        if (!validTargets.isEmpty() && sentCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    firstErrorMessage == null || firstErrorMessage.isBlank()
                            ? "La passerelle n'a accepté aucun message"
                            : "Envoi refusé: " + firstErrorMessage
            );
        }

        subscriptionService.debitSms(company, sentUnits, "Envoi express " + channel.name());

        return new ManualSmsResponse(
                targets.size(),
                sentCount,
                unitsPerRecipient,
                sentUnits,
                company.getSmsBalance()
        );
    }
}
