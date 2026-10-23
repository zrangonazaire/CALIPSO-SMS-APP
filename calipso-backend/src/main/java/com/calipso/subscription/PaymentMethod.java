package com.calipso.subscription;

public enum PaymentMethod {
    WAVE_CI("Wave Côte d'Ivoire"),
    ORANGE_MONEY_CI("Orange Money CI"),
    MTN_MOMO_CI("MTN MoMo CI"),
    MOOV_MONEY_CI("Moov Money CI"),
    CARD("Carte Bancaire / Djamo"),
    SYSTEM("Système Calipso");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
