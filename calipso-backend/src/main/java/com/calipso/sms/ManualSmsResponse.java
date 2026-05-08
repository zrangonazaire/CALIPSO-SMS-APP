package com.calipso.sms;

public record ManualSmsResponse(
        int requestedRecipients,
        int acceptedRecipients,
        int segmentsPerRecipient,
        int totalSegments,
        int remainingSmsBalance
) {
}
