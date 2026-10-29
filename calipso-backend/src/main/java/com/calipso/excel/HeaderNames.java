package com.calipso.excel;

import java.text.Normalizer;
import java.util.Locale;

public final class HeaderNames {
    private HeaderNames() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }
}
