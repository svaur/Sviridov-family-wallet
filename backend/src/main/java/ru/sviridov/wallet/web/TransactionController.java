package ru.sviridov.wallet.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.TransactionService;
import ru.sviridov.wallet.web.dto.TransactionResponse;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionResponse> list(@AuthenticationPrincipal AppUser user) {
        return transactionService.list(user.getId());
    }
}
