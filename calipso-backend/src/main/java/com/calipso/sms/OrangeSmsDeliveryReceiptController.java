package com.calipso.sms;

import com.calipso.auth.CompanyAccess;

import com.calipso.recipient.RecipientStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orange/sms-delivery-receipts")
@RequiredArgsConstructor
@Slf4j
public class OrangeSmsDeliveryReceiptController {

    private final CompanyAccess access;

    private final SmsSendHistoryRepository historyRepository;

    @PostMapping
    @Transactional
    public ResponseEntity<Void> receive(@RequestBody Map<String, Object> body) {
        access.requirePlatformAdmin();
        JsonNode payload = new ObjectMapper().valueToTree(body);
        JsonNode notification = payload.path("deliveryInfoNotification");
        String deliveryCallbackData = textOrNull(notification.path("callbackData"));
        String deliveryStatus = textOrNull(notification.path("deliveryInfo").path("deliveryStatus"));
        String recipientAddress = textOrNull(notification.path("deliveryInfo").path("address"));

        log.info(
                "[Orange SMS DR] Accuse recu: callbackData={}, deliveryStatus={}, address={}, payload={}",
                deliveryCallbackData,
                deliveryStatus,
                recipientAddress,
                payload
        );

        if ((deliveryCallbackData == null || deliveryCallbackData.isBlank())
                && (recipientAddress == null || recipientAddress.isBlank())) {
            log.warn("[Orange SMS DR] Accuse ignore: callbackData et address absents");
            return ResponseEntity.ok().build();
        }

        List<SmsSendHistory> histories = findHistories(deliveryCallbackData, recipientAddress);

        if (histories.isEmpty()) {
            log.warn(
                    "[Orange SMS DR] Aucun historique trouve pour callbackData={}, address={}",
                    deliveryCallbackData,
                    recipientAddress
            );
            return ResponseEntity.ok().build();
        }

        LocalDateTime now = LocalDateTime.now();
        for (SmsSendHistory history : histories) {
            history.setDeliveryStatus(deliveryStatus);
            history.setDeliveryReceiptPayload(payload.toString());

            if ("DeliveredToTerminal".equalsIgnoreCase(deliveryStatus)) {
                history.setStatus(RecipientStatus.DELIVERED);
                history.setDeliveredAt(now);
                if (history.getSentAt() == null) {
                    history.setSentAt(now);
                }
            } else if ("DeliveryImpossible".equalsIgnoreCase(deliveryStatus)) {
                history.setStatus(RecipientStatus.FAILED);
                history.setErrorMessage("Orange delivery receipt: DeliveryImpossible");
            }
        }

        historyRepository.saveAll(histories);
        return ResponseEntity.ok().build();
    }

    private List<SmsSendHistory> findHistories(String deliveryCallbackData, String recipientAddress) {
        if (deliveryCallbackData != null && !deliveryCallbackData.isBlank()) {
            List<SmsSendHistory> histories = historyRepository
                    .findByDeliveryCallbackDataOrderByCreatedAtDesc(deliveryCallbackData);

            if (!histories.isEmpty()) {
                return histories;
            }

            histories = historyRepository.findByProviderResourceIdOrderByCreatedAtDesc(deliveryCallbackData);
            if (!histories.isEmpty()) {
                return histories;
            }

            histories = latestByPhone(normalizeReceiptAddress(deliveryCallbackData));
            if (!histories.isEmpty()) {
                return histories;
            }
        }

        return latestByPhone(normalizeReceiptAddress(recipientAddress));
    }

    private List<SmsSendHistory> latestByPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return List.of();
        }

        return historyRepository.findByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .stream()
                .limit(1)
                .toList();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String normalizeReceiptAddress(String address) {
        if (address == null) {
            return null;
        }

        String normalized = address.trim();
        if (normalized.regionMatches(true, 0, "tel:", 0, "tel:".length())) {
            normalized = normalized.substring("tel:".length());
        }

        return normalized;
    }
}
