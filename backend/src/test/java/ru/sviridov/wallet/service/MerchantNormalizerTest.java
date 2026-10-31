package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormalizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @Test
    void lowercasesTrimsAndCollapsesWhitespace() {
        assertThat(normalizer.normalize("  MIR   KORMA  ")).isEqualTo("mir korma");
    }

    @Test
    void keepsMirPerSpecExample() {
        assertThat(normalizer.normalize("MIR KORMA")).isEqualTo("mir korma");
    }

    @Test
    void stripsServicePrefixes() {
        assertThat(normalizer.normalize("ОПЛАТА MIR KORMA")).isEqualTo("mir korma");
    }

    @Test
    void normalizeCoreKeepsPrefixes() {
        assertThat(normalizer.normalizeCore("ОПЛАТА  MIR   KORMA")).isEqualTo("оплата mir korma");
    }

    @Test
    void returnsNullForBlankOrNull() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("   ")).isNull();
        assertThat(normalizer.normalizeCore("")).isNull();
    }
}
