package ru.sviridov.wallet.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.TransactionService;
import ru.sviridov.wallet.web.dto.CategoryAssignRequest;
import ru.sviridov.wallet.web.dto.CommentRequest;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        transactionService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/category")
    public ResponseEntity<TransactionResponse> assignCategory(@AuthenticationPrincipal AppUser user,
                                                              @PathVariable Long id,
                                                              @Valid @RequestBody CategoryAssignRequest request) {
        TransactionResponse updated = transactionService.assignCategory(user.getId(), id, request.categoryId());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<TransactionResponse> markTransfer(@AuthenticationPrincipal AppUser user,
                                                            @PathVariable Long id) {
        TransactionResponse updated = transactionService.markTransfer(user.getId(), id);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/comment")
    public ResponseEntity<TransactionResponse> updateComment(@AuthenticationPrincipal AppUser user,
                                                             @PathVariable Long id,
                                                             @Valid @RequestBody CommentRequest request) {
        TransactionResponse updated = transactionService.updateComment(user.getId(), id, request.comment());
        return ResponseEntity.ok(updated);
    }
}
