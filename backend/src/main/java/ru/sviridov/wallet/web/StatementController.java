package ru.sviridov.wallet.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.StatementService;
import ru.sviridov.wallet.web.dto.StatementImportResponse;
import ru.sviridov.wallet.web.dto.StatementResponse;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/cards/{cardId}/statements")
public class StatementController {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @PostMapping
    public ResponseEntity<StatementImportResponse> upload(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long cardId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Файл не выбран или пуст");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Файл больше 10 МБ");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Не удалось прочитать файл");
        }
        StatementImportResponse response = statementService.importStatement(user.getId(), cardId, bytes);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public List<StatementResponse> list(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long cardId) {
        return statementService.listForCard(user.getId(), cardId).stream()
                .map(StatementResponse::from)
                .toList();
    }
}
