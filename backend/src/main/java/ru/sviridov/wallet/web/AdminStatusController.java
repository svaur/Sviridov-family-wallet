package ru.sviridov.wallet.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.service.ServerStatusService;
import ru.sviridov.wallet.web.dto.ServerStatusResponse;

/**
 * Health snapshot for the admin "Сервер" page. Under {@code /api/admin/**}.
 */
@RestController
@RequestMapping("/api/admin/status")
public class AdminStatusController {

    private final ServerStatusService serverStatusService;

    public AdminStatusController(ServerStatusService serverStatusService) {
        this.serverStatusService = serverStatusService;
    }

    @GetMapping
    public ServerStatusResponse status() {
        return serverStatusService.status();
    }
}
