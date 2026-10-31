package ru.sviridov.wallet.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.StatementGapService;
import ru.sviridov.wallet.web.dto.StatementGapResponse;

import java.util.List;

@RestController
@RequestMapping("/api/statement-gaps")
public class StatementGapController {

    private final StatementGapService statementGapService;

    public StatementGapController(StatementGapService statementGapService) {
        this.statementGapService = statementGapService;
    }

    @GetMapping
    public List<StatementGapResponse> list(@AuthenticationPrincipal AppUser user) {
        return statementGapService.list(user.getId()).stream()
                .map(StatementGapResponse::from)
                .toList();
    }
}
