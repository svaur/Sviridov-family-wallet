package ru.sviridov.wallet.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Normalises merchant names for matching and dedup.
 *
 * <p>{@link #normalize} produces the stored {@code merchant_normalized} value:
 * lowercase + trim + whitespace collapse + strip of service prefixes (noise
 * that some bank feeds prepend to the counterparty name, e.g. "ОПЛАТА …").
 *
 * <p>{@link #normalizeCore} is the stable, minimal form (lowercase + trim +
 * collapse) used for the transaction fingerprint so dedup identity does not
 * change when the prefix list evolves.
 */
@Component
public class MerchantNormalizer {

    /** Leading service/payment noise tokens, matched against the already lowercased string. */
    private static final Set<String> SERVICE_PREFIXES = Set.of(
            "visa", "mastercard", "mc", "maestro",
            "оплата", "платёж", "платеж", "покупка", "перевод", "зачисление", "возврат", "списание", "комиссия");

    public String normalize(String raw) {
        String core = normalizeCore(raw);
        if (core == null) {
            return null;
        }
        return stripServicePrefixes(core);
    }

    public String normalizeCore(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String stripServicePrefixes(String value) {
        String result = value;
        boolean stripped;
        do {
            stripped = false;
            for (String prefix : SERVICE_PREFIXES) {
                if (result.startsWith(prefix + " ")) {
                    result = result.substring(prefix.length()).trim();
                    stripped = true;
                }
            }
        } while (stripped);
        return result.isEmpty() ? null : result;
    }
}
