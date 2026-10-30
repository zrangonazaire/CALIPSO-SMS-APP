package com.calipso.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class PaymentConfigurationTest {
    @Test void checkoutCreationDoesNotCountAsAConfirmedPayment() {
        var gateway = new MobilePaymentGatewayService();
        assertEquals(PaymentStatus.PENDING, gateway.pendingCheckout(201, "https://pay.wave.com/test", "REF",
                PaymentMethod.WAVE_CI, null, "WAVE", "Paiement initié").status());
        assertThrows(ResponseStatusException.class, () -> gateway.pendingCheckout(400, "https://pay.wave.com/test", "REF",
                PaymentMethod.WAVE_CI, null, "WAVE", "Erreur"));
        assertThrows(ResponseStatusException.class, () -> gateway.pendingCheckout(200, null, "REF",
                PaymentMethod.CARD, null, "CINETPAY", "Erreur"));
    }
    @Test void missingPaymentProviderNeverSimulatesASuccessfulPurchase() {
        var gateway = new MobilePaymentGatewayService();
        var exception = assertThrows(ResponseStatusException.class, () -> gateway.processPayment(
                8L, "MAGISER", 1_200_000, 60_000, PaymentMethod.CARD, null, "Forfait Complet annuel"));
        assertEquals(503, exception.getStatusCode().value());
    }
}
