package ru.sviridov.wallet.web;

import jakarta.validation.Valid;
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
import ru.sviridov.wallet.web.dto.ConfirmRequest;
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

    /** Step 1: parse the uploaded PDF (or screenshot) and return a categorised preview. */
    @PostMapping("/preview")
    public StatementPreviewResponse preview(
            @AuthenticationPrincipal AppUser user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kind", defaultValue = "statement") String kind) {
        boolean screenshot = "screenshot".equalsIgnoreCase(kind);
        return statementService.preview(user.getId(), readBytes(file), screenshot);
    }

    /** Step 2: re-apply the current rules to an in-progress row set (stateless). */
    @PostMapping("/categorize")
    public List<StatementPreviewResponse.PreviewTransaction> categorize(
            @AuthenticationPrincipal AppUser user,
            @RequestBody List<StatementPreviewResponse.PreviewTransaction> transactions) {
        return statementService.categorize(user.getId(), transactions);
    }

    /** Step 3: persist the final rows and return the import result. */
    @PostMapping("/confirm")
    public StatementImportResponse confirm(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody ConfirmRequest request) {
        return statementService.confirm(user.getId(), request);
    }

    @GetMapping
    public List<StatementResponse> list(@AuthenticationPrincipal AppUser user) {
        return statementService.listAll(user.getId()).stream()
                .map(StatementResponse::from)
                .toList();
    }

    /** Deletes an imported statement and all of its transactions (hard delete). */
    @DeleteMapping("/{id}")
    public int delete(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return statementService.deleteStatement(user.getId(), id);
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
