package ru.sviridov.wallet.web;

import java.time.Instant;

public record AppInfo(String name, String version, String time) {

    public static AppInfo now() {
        return new AppInfo("sviridov-family-wallet", "0.1.0", Instant.now().toString());
    }
}
