package com.calipso.sms;

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

    private final CompanyRepository companyRepository;
    private final SmsMessageService smsMessageService;
    private final SmsSendHistoryRepository historyRepository;
    private final SubscriptionService subscriptionService;
    private final OrangeSmsClient orangeSmsClient;

    @PostMapping("/send")
    public ManualSmsResponse send(@RequestBody @Valid ManualSmsRequest request) {
        log.info("[Manual SMS] Payload recu du frontend: {}", request);

        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        List<String> phoneNumbers = request.phoneNumbers() == null ? List.of() : request.phoneNumbers();
        List<String> validNumbers = phoneNumbers.stream()
                .filter(number -> number != null && !number.isBlank())
                .toList();

        int segmentsPerRecipient = smsMessageService.calculateSmsSegments(request.message());
        int totalSegments = validNumbers.size() * segmentsPerRecipient;

        log.info(
                "[Manual SMS] Requete recue: companyId={}, companyName={}, senderPhone={}, smsBalance={}, requestedRecipients={}, validRecipients={}, phoneNumbers={}, message={}, segmentsPerRecipient={}, totalSegments={}",
                company.getId(),
                company.getName(),
                company.getSenderPhone(),
                company.getSmsBalance(),
                phoneNumbers.size(),
                validNumbers.size(),
                phoneNumbers,
                request.message(),
                segmentsPerRecipient,
                totalSegments
        );

        subscriptionService.validateSmsBalance(company, totalSegments);

        int sentCount = 0;
        String firstErrorMessage = null;
        for (String phoneNumber : validNumbers) {
            String normalizedPhone = orangeSmsClient.normalizePhone(phoneNumber);
            log.info(
                    "[Manual SMS] Envoi destinataire: companyId={}, senderPhone={}, phoneOriginal={}, phoneNormalise={}, message={}",
                    company.getId(),
                    company.getSenderPhone(),
                    phoneNumber,
                    normalizedPhone,
                    request.message()
            );

            SmsDeliveryResult deliveryResult = orangeSmsClient.sendSms(
                    request.message(),
                    company.getSenderPhone(),
                    normalizedPhone
            );

            log.info(
                    "[Manual SMS] Resultat destinataire: phone={}, acceptedByOrange={}, statusCode={}, providerResourceId={}, error={}",
                    normalizedPhone,
                    deliveryResult.sent(),
                    deliveryResult.statusCode(),
                    deliveryResult.providerResourceId(),
                    deliveryResult.errorMessage()
            );

            if (deliveryResult.sent()) {
                sentCount++;
            } else if (firstErrorMessage == null) {
                firstErrorMessage = deliveryResult.errorMessage();
            }

            historyRepository.save(SmsSendHistory.builder()
                    .company(company)
                    .source(SmsSendSource.MANUAL)
                    .phoneNumber(normalizedPhone)
                    .message(request.message())
                    .segmentCount(segmentsPerRecipient)
                    .status(deliveryResult.sent() ? RecipientStatus.SENT : RecipientStatus.FAILED)
                    .errorMessage(deliveryResult.errorMessage())
                    .providerResponse(deliveryResult.providerResponse())
                    .providerResourceUrl(deliveryResult.providerResourceUrl())
                    .providerResourceId(deliveryResult.providerResourceId())
                    .sentAt(deliveryResult.sent() ? java.time.LocalDateTime.now() : null)
                    .build());
        }

        int sentSegments = sentCount * segmentsPerRecipient;
        if (!validNumbers.isEmpty() && sentCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    firstErrorMessage == null || firstErrorMessage.isBlank()
                            ? "Orange n'a accepte aucun SMS"
                            : "Orange n'a accepte aucun SMS: " + firstErrorMessage
            );
        }

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
