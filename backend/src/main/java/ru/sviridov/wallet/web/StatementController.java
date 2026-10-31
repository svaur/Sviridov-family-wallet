package ru.sviridov.wallet.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.StatementService;
import ru.sviridov.wallet.web.dto.CategoryAssignRequest;
import ru.sviridov.wallet.web.dto.StatementImportResponse;
import ru.sviridov.wallet.web.dto.StatementPreviewResponse;
import ru.sviridov.wallet.web.dto.StatementResponse;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    /** Step 1/2: parse the uploaded PDF and return a categorised preview. */
    @PostMapping("/preview")
    public StatementPreviewResponse preview(
            @AuthenticationPrincipal AppUser user,
            @RequestParam("file") MultipartFile file) {
        return statementService.preview(user.getId(), readBytes(file));
    }

    /** Step 2: re-apply the current rules to the draft. */
    @PostMapping("/preview/{draftId}/recategorize")
    public StatementPreviewResponse recategorize(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String draftId) {
        return statementService.recategorize(user.getId(), draftId);
    }

    /** Step 2: remove one transaction from the draft. */
    @DeleteMapping("/preview/{draftId}/transactions/{rowNumber}")
    public StatementPreviewResponse removeTransaction(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String draftId,
            @PathVariable int rowNumber) {
        return statementService.removeTransaction(user.getId(), draftId, rowNumber);
    }

    /** Step 2: assign a category manually to one transaction in the draft. */
    @PostMapping("/preview/{draftId}/transactions/{rowNumber}/category")
    public StatementPreviewResponse assignDraftCategory(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String draftId,
            @PathVariable int rowNumber,
            @RequestBody CategoryAssignRequest request) {
        return statementService.assignDraftCategory(user.getId(), draftId, rowNumber, request.categoryId());
    }

    /** Step 3: import the draft and return the final result. */
    @PostMapping("/preview/{draftId}/confirm")
    public StatementImportResponse confirm(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String draftId) {
        return statementService.confirm(user.getId(), draftId);
    }

    @GetMapping
    public List<StatementResponse> list(@AuthenticationPrincipal AppUser user) {
        return statementService.listAll(user.getId()).stream()
                .map(StatementResponse::from)
                .toList();
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Файл не выбран или пуст");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Файл больше 10 МБ");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Не удалось прочитать файл");
        }
    }
}
