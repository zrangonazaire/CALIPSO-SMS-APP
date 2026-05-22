package com.calipso.sms;

public record OrangeSmsContract(
        String id,
        String type,
        String country,
        String offerName,
        Integer availableUnits,
        Integer requestedUnits,
        String status,
        String expirationDate,
        String creationDate,
        String lastUpdateDate
) {
}
