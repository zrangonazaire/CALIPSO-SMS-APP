package com.calipso.sms;

import com.calipso.recipient.RecipientStatus;
import com.fasterxml.jackson.databind.JsonNode;
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

@RestController
@RequestMapping("/api/v1/orange/sms-delivery-receipts")
@RequiredArgsConstructor
@Slf4j
public class OrangeSmsDeliveryReceiptController {

    private final SmsSendHistoryRepository historyRepository;

    @PostMapping
    @Transactional
    public ResponseEntity<Void> receive(@RequestBody JsonNode payload) {
        JsonNode notification = payload.path("deliveryInfoNotification");
        String providerResourceId = textOrNull(notification.path("callbackData"));
        String deliveryStatus = textOrNull(notification.path("deliveryInfo").path("deliveryStatus"));
        String recipientAddress = textOrNull(notification.path("deliveryInfo").path("address"));

        log.info(
                "[Orange SMS DR] Accuse recu: providerResourceId={}, deliveryStatus={}, address={}, payload={}",
                providerResourceId,
                deliveryStatus,
                recipientAddress,
                payload
        );

        if (providerResourceId == null || providerResourceId.isBlank()) {
            log.warn("[Orange SMS DR] Accuse ignore: callbackData/resource_id absent");
            return ResponseEntity.ok().build();
        }

        List<SmsSendHistory> histories = historyRepository.findByProviderResourceIdOrderByCreatedAtDesc(providerResourceId);
        if (histories.isEmpty()) {
            log.warn("[Orange SMS DR] Aucun historique trouve pour providerResourceId={}", providerResourceId);
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

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
