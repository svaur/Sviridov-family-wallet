package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void lowercasesTrimsAndCollapsesWhitespace() {
        assertThat(normalizer.normalize("  MIR   KORMA  ")).isEqualTo("mir korma");
    }

    @Test
    void keepsWholeStringWithoutPrefixStripping() {
        assertThat(normalizer.normalize("ОПЛАТА MIR KORMA")).isEqualTo("оплата mir korma");
    }

    @Test
    void returnsNullForBlankOrNull() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("   ")).isNull();
        assertThat(normalizer.normalize("")).isNull();
    }
}
