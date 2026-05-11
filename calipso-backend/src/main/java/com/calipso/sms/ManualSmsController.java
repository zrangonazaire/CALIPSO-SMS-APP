package com.calipso.sms;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
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
        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();

        if (totalSegments > currentBalance) {
            throw new RuntimeException("Solde SMS insuffisant");
        }

        company.setSmsBalance(currentBalance - totalSegments);
        companyRepository.save(company);

        validNumbers.forEach(phoneNumber -> historyRepository.save(SmsSendHistory.builder()
                .company(company)
                .source(SmsSendSource.MANUAL)
                .phoneNumber(normalizePhone(phoneNumber))
                .message(request.message())
                .segmentCount(segmentsPerRecipient)
                .build()));

        return new ManualSmsResponse(
                phoneNumbers.size(),
                validNumbers.size(),
                segmentsPerRecipient,
                totalSegments,
                company.getSmsBalance()
        );
    }

    private String normalizePhone(String phone) {
        String cleaned = phone.replace(" ", "")
                .replace("-", "")
                .replace(".", "")
                .trim();

        if (cleaned.startsWith("+")) {
            return cleaned;
        }

        if (cleaned.startsWith("225")) {
            return "+" + cleaned;
        }

        if (cleaned.startsWith("01") || cleaned.startsWith("05") || cleaned.startsWith("07")) {
            return "+225" + cleaned;
        }

        return cleaned;
    }
}
