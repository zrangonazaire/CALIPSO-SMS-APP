package com.calipso.sms;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SmsMessageService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_\\-]+)\\s*}}");

    public String generateMessage(String template, Map<String, Object> data) {
        if (template == null) {
            return "";
        }
        if (data == null || data.isEmpty()) {
            return template;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = findCaseInsensitive(data, key);
            String replacement = val == null ? "" : val.toString();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private Object findCaseInsensitive(Map<String, Object> data, String key) {
        if (data.containsKey(key)) {
            return data.get(key);
        }
        String normalized = key.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        if (data.containsKey(normalized)) {
            return data.get(normalized);
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(normalized)) {
                return entry.getValue();
            }
        }
        return null;
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
