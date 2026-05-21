package com.calipso.sms;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.recipient.RecipientStatus;
import com.calipso.subscription.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manual-sms")
@RequiredArgsConstructor
public class ManualSmsController {

    private final CompanyRepository companyRepository;
    private final SmsMessageService smsMessageService;
    private final SmsSendHistoryRepository historyRepository;
    private final SubscriptionService subscriptionService;
    private final OrangeSmsClient orangeSmsClient;

    @PostMapping("/send")
    public ManualSmsResponse send(@RequestBody @Valid ManualSmsRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        List<String> phoneNumbers = request.phoneNumbers() == null ? List.of() : request.phoneNumbers();
        List<String> validNumbers = phoneNumbers.stream()
                .filter(number -> number != null && !number.isBlank())
                .toList();

        int segmentsPerRecipient = smsMessageService.calculateSmsSegments(request.message());
        int totalSegments = validNumbers.size() * segmentsPerRecipient;
        subscriptionService.validateSmsBalance(company, totalSegments);

        int sentCount = 0;
        for (String phoneNumber : validNumbers) {
            String normalizedPhone = orangeSmsClient.normalizePhone(phoneNumber);
            SmsDeliveryResult deliveryResult = orangeSmsClient.sendSms(
                    request.message(),
                    company.getSenderPhone(),
                    normalizedPhone,
                    company.getName()
            );

            if (deliveryResult.sent()) {
                sentCount++;
            }

            historyRepository.save(SmsSendHistory.builder()
                    .company(company)
                    .source(SmsSendSource.MANUAL)
                    .phoneNumber(normalizedPhone)
                    .message(request.message())
                    .segmentCount(segmentsPerRecipient)
                    .status(deliveryResult.sent() ? RecipientStatus.SENT : RecipientStatus.FAILED)
                    .errorMessage(deliveryResult.errorMessage())
                    .build());
        }

        int sentSegments = sentCount * segmentsPerRecipient;
        subscriptionService.debitSms(company, sentSegments, "Envoi SMS manuel");

        return new ManualSmsResponse(
                phoneNumbers.size(),
                sentCount,
                segmentsPerRecipient,
                sentSegments,
                company.getSmsBalance()
        );
    }
}
