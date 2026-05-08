package com.calipso.sms;


import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SmsMessageService {

    public String generateMessage(String template, Map<String, Object> data) {
        String message = template;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String variable = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() == null ? "" : entry.getValue().toString();

            message = message.replace(variable, value);
        }

        return message;
    }

    public int calculateSmsSegments(String message) {
        if (message == null || message.isBlank()) {
            return 0;
        }

        boolean unicode = containsUnicodeCharacters(message);

        int singleLimit = unicode ? 70 : 160;
        int multiLimit = unicode ? 67 : 153;

        int length = message.length();

        if (length <= singleLimit) {
            return 1;
        }

        return (int) Math.ceil((double) length / multiLimit);
    }

    private boolean containsUnicodeCharacters(String message) {
        for (char c : message.toCharArray()) {
            if (c > 127) {
                return true;
            }
        }
        return false;
    }
}