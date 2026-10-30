package ru.sviridov.wallet.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RootControllerTest {

    private final RootController controller = new RootController();

    @Test
    void infoReturnsExpectedAppName() {
        AppInfo info = controller.info();
        assertThat(info.name()).isEqualTo("sviridov-family-wallet");
        assertThat(info.version()).isNotBlank();
        assertThat(info.time()).isNotBlank();
    }
}
