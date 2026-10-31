package ru.sviridov.wallet.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Minimal text normalisation for case-insensitive rule matching: lowercase +
 * trim + whitespace collapse. Blank input yields {@code null}.
 */
@Component
public class TextNormalizer {

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
