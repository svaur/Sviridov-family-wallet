package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.Statement;
import ru.sviridov.wallet.domain.StatementRepository;
import ru.sviridov.wallet.domain.Transaction;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.parser.ParsedRow;
import ru.sviridov.wallet.parser.ParsedStatement;
import ru.sviridov.wallet.parser.PdfBoxStatementParser;
import ru.sviridov.wallet.parser.ScreenshotParser;
import ru.sviridov.wallet.parser.StatementParseException;
import ru.sviridov.wallet.parser.StatementParser;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.ConfirmRequest;
import ru.sviridov.wallet.web.dto.StatementImportResponse;
import ru.sviridov.wallet.web.dto.StatementPreviewResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses bank statements (PDF) and bank-feed screenshots (OCR) and imports
 * them. The upload wizard is stateless on the backend: {@link #preview} parses
 * and categorises without storing anything, {@link #categorize} re-applies the
 * rules to an in-progress row set, and {@link #confirm} persists the final rows.
 * The frontend holds the rows between these calls.
 */
@Service
public class StatementService {

    private final CardService cardService;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final PdfBoxStatementParser parser;
    private final ScreenshotParser screenshotParser;
    private final CategorizationEngine categorizationEngine;
    private final StatementGapService statementGapService;

    public StatementService(CardService cardService, StatementRepository statementRepository,
                            TransactionRepository transactionRepository, RuleRepository ruleRepository,
                            CategoryRepository categoryRepository, PdfBoxStatementParser parser,
                            ScreenshotParser screenshotParser, CategorizationEngine categorizationEngine,
                            StatementGapService statementGapService) {
        this.cardService = cardService;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.parser = parser;
        this.screenshotParser = screenshotParser;
        this.categorizationEngine = categorizationEngine;
        this.statementGapService = statementGapService;
    }

    /**
     * Step 1: parse, validate and categorise the uploaded PDF/screenshot without
     * persisting anything. Returns the rows together with metadata and warnings;
     * the card is NOT created here — it is resolved on {@link #confirm}.
     *
     * @param screenshot {@code true} when the bytes are a bank-feed screenshot
     *                   image (OCR), {@code false} for a PDF statement.
     */
    public StatementPreviewResponse preview(Long userId, byte[] bytes, boolean screenshot) {
        StatementParser activeParser = screenshot ? screenshotParser : parser;
        ParsedStatement parsed;
        try {
            parsed = activeParser.parse(bytes);
        } catch (StatementParseException e) {
            throw new BadRequestException("Не удалось распознать выписку: " + e.getMessage());
        }

        List<String> warnings = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>(parsed.unrecognizedRows());
        List<ParsedRow> validRows = new ArrayList<>();

        int prevRowNumber = 0;
        for (ParsedRow row : parsed.rows()) {
            boolean xorOk = (row.expense() != null) != (row.income() != null);
            if (!xorOk || row.amount() == null) {
                unrecognized.add("Строка " + row.rowNumber() + ": некорректная сумма/колонка");
                continue;
            }
            if (row.amount().signum() < 0) {
                unrecognized.add("Строка " + row.rowNumber() + ": отрицательная сумма");
                continue;
            }
            if (row.amount().signum() == 0) {
                // The parser keeps rows whose amount it could not read, with a zero
                // amount, so the user can correct them in the wizard instead of
                // re-creating the operation by hand.
                warnings.add("Строка " + row.rowNumber() + ": сумма не распознана (0 ₽) — проверьте");
            }
            if (row.date() == null) {
                unrecognized.add("Строка " + row.rowNumber() + ": дата не определена");
                continue;
            }
            if (parsed.periodFrom() != null && parsed.periodTo() != null
                    && (row.date().isBefore(parsed.periodFrom()) || row.date().isAfter(parsed.periodTo()))) {
                warnings.add("Строка " + row.rowNumber() + ": дата вне периода выписки");
            }
            if (prevRowNumber > 0 && row.rowNumber() <= prevRowNumber) {
                warnings.add("Строка " + row.rowNumber() + ": номер не монотонный");
            }
            prevRowNumber = row.rowNumber();
            validRows.add(row);
        }

        // Warn about an overlapping period via a read-only card lookup (no creation).
        if (parsed.periodFrom() != null && parsed.periodTo() != null) {
            cardService.findByLast4(userId, parsed.cardNumber()).ifPresent(card -> {
                if (statementRepository.existsOverlapping(card.getId(), parsed.periodFrom(), parsed.periodTo())) {
                    warnings.add("Период выписки пересекается с уже загруженной выпиской");
                }
            });
        }

        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);
        Map<Long, String> categoryNames = categoryNames(userId);

        List<StatementPreviewResponse.PreviewTransaction> transactions = new ArrayList<>();
        for (ParsedRow row : validRows) {
            Transaction tx = toTransaction(userId, row);
            Long categoryId = categorizationEngine.categorize(tx, rules);
            transactions.add(new StatementPreviewResponse.PreviewTransaction(
                    row.rowNumber(), row.date(), row.operation(), row.purpose(), row.amount(),
                    row.type(), categoryId, categoryName(categoryId, categoryNames), false));
        }

        return new StatementPreviewResponse(activeParser.version(), parsed.periodFrom(), parsed.periodTo(),
                parsed.cardNumber(), parsed.contract(), parsed.account(), parsed.rowCountRaw(),
                validRows.size(), warnings, unrecognized, transactions);
    }

    /**
     * Step 2: re-apply the current rule set to an in-progress row set. Rows
     * marked {@code categoryManual} keep their category; the engine is the same
     * one used at parse time. Stateless — the rows live on the frontend.
     */
    public List<StatementPreviewResponse.PreviewTransaction> categorize(
            Long userId, List<StatementPreviewResponse.PreviewTransaction> transactions) {
        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);
        Map<Long, String> categoryNames = categoryNames(userId);
        List<StatementPreviewResponse.PreviewTransaction> result = new ArrayList<>();
        for (StatementPreviewResponse.PreviewTransaction p : transactions) {
            Long categoryId = p.categoryId();
            if (!p.categoryManual()) {
                categoryId = categorizationEngine.categorize(toTransaction(userId, p), rules);
            }
            result.add(new StatementPreviewResponse.PreviewTransaction(
                    p.rowNumber(), p.date(), p.operation(), p.purpose(), p.amount(), p.type(),
                    categoryId, categoryName(categoryId, categoryNames), p.categoryManual()));
        }
        return result;
    }

    /**
     * Step 3: persist the final rows. Resolves/creates the card from the
     * confirmed number, dedups by fingerprint and saves the statement.
     */
    @Transactional
    public StatementImportResponse confirm(Long userId, ConfirmRequest request) {
        Card card = cardService.findOrCreateByNumber(userId, request.cardNumber());
        Long cardId = card.getId();
        boolean screenshot = isScreenshot(request.parserVersion());

        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);

        List<Transaction> candidates = new ArrayList<>();
        for (StatementPreviewResponse.PreviewTransaction p : request.transactions()) {
            Transaction tx = toTransaction(userId, p);
            tx.setCardId(cardId);
            tx.setSource(screenshot ? Transaction.SOURCE_SCREENSHOT : Transaction.SOURCE_STATEMENT);
            Long categoryId = p.categoryManual() ? p.categoryId() : categorizationEngine.categorize(tx, rules);
            tx.setCategoryId(categoryId);
            tx.setCategoryManual(p.categoryManual());
            tx.setFingerprint(fingerprint(userId, cardId, tx));
            candidates.add(tx);
        }

        List<String> fingerprints = candidates.stream().map(Transaction::getFingerprint).toList();
        Set<String> existing = fingerprints.isEmpty()
                ? Set.of()
                : transactionRepository.findExistingFingerprints(fingerprints);

        int skippedDuplicates = 0;
        List<Transaction> toSave = new ArrayList<>();
        for (Transaction tx : candidates) {
            if (existing.contains(tx.getFingerprint())) {
                skippedDuplicates++;
                continue;
            }
            toSave.add(tx);
        }

        String status = request.unrecognizedRows().isEmpty() ? "OK" : "PARTIAL";

        // A re-import that is entirely duplicates is not a new statement: keeping
        // an empty metadata row would pollute the statement list, the overlap
        // warning and the gap detector with a file that contributed nothing.
        Long statementId = null;
        if (!toSave.isEmpty()) {
            Statement statement = statementRepository.save(new Statement(cardId,
                    request.periodFrom(), request.periodTo(), request.parserVersion(), status,
                    toSave.size(), request.rowCountRaw(), request.cardNumber().trim(),
                    request.contract(), request.account()));
            statementId = statement.getId();
            for (Transaction t : toSave) {
                t.setStatementId(statementId);
            }
            transactionRepository.saveAll(toSave);
        }
        statementGapService.detectGaps(cardId);

        return new StatementImportResponse(statementId, status, request.rowCountRaw(),
                toSave.size(), skippedDuplicates, request.warnings(), request.unrecognizedRows());
    }

    @Transactional(readOnly = true)
    public List<Statement> listAll(Long userId) {
        return statementRepository.findByUserIdOrderByIdDesc(userId);
    }

    /**
     * Deletes an imported statement together with all transactions that came
     * from it. Transactions are HARD-deleted (including soft-deleted tombstones)
     * so a re-upload of the same statement imports them again fresh, while the
     * statement metadata row is also removed. Manual transactions (statement_id
     * is null) are not touched.
     *
     * @return the number of removed transactions.
     */
    @Transactional
    public int deleteStatement(Long userId, Long statementId) {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new NotFoundException("Выписка не найдена"));
        // Ownership check: throws NotFoundException when the card is not the user's.
        cardService.get(userId, statement.getCardId());

        List<Transaction> transactions = transactionRepository.findByStatementId(statementId);
        transactionRepository.deleteAll(transactions);

        statementRepository.deleteById(statementId);
        statementGapService.detectGaps(statement.getCardId());
        return transactions.size();
    }

    private Transaction toTransaction(Long userId, ParsedRow row) {
        return new Transaction(userId, null, row.rowNumber(), row.date(), row.operation(),
                row.purpose(), row.amount(), row.type(), null);
    }

    private Transaction toTransaction(Long userId, StatementPreviewResponse.PreviewTransaction p) {
        return new Transaction(userId, null, p.rowNumber(), p.date(), p.operation(), p.purpose(),
                p.amount(), p.type(), null);
    }

    private Map<Long, String> categoryNames(Long userId) {
        return categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private static String categoryName(Long categoryId, Map<Long, String> categoryNames) {
        return categoryId == null ? null : categoryNames.get(categoryId);
    }

    private static boolean isScreenshot(String parserVersion) {
        return parserVersion != null && parserVersion.startsWith("screenshot");
    }

    private static String fingerprint(Long userId, Long cardId, Transaction tx) {
        String purpose = tx.getPurpose() == null ? "" : tx.getPurpose().trim().toLowerCase().replaceAll("\\s+", " ");
        String raw = userId + "|" + cardId + "|" + tx.getDate() + "|" + tx.getAmount().toPlainString()
                + "|" + tx.getType() + "|" + purpose;
        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
