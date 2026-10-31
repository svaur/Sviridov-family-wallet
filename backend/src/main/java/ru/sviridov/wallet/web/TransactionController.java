package ru.sviridov.wallet.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.TransactionService;
import ru.sviridov.wallet.web.dto.TransactionRequest;
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

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal AppUser user,
                                                      @Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = transactionService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
