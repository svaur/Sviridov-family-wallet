package ru.sviridov.wallet.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RootController {

    @GetMapping
    public AppInfo info() {
        return AppInfo.now();
    }
}
