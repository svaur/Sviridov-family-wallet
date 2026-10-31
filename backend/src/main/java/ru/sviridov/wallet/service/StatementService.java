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
import ru.sviridov.wallet.parser.StatementParseException;
import ru.sviridov.wallet.parser.StatementParser;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
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

@Service
public class StatementService {

    private final CardService cardService;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final StatementParser parser;
    private final CategorizationEngine categorizationEngine;
    private final StatementDraftStore draftStore;
    private final StatementGapService statementGapService;

    public StatementService(CardService cardService, StatementRepository statementRepository,
                            TransactionRepository transactionRepository, RuleRepository ruleRepository,
                            CategoryRepository categoryRepository, StatementParser parser,
                            CategorizationEngine categorizationEngine, StatementDraftStore draftStore,
                            StatementGapService statementGapService) {
        this.cardService = cardService;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.parser = parser;
        this.categorizationEngine = categorizationEngine;
        this.draftStore = draftStore;
        this.statementGapService = statementGapService;
    }

    /**
     * Step 1 of the upload wizard: parse, validate and categorise the PDF
     * without persisting transactions, and keep the result as a draft. The card
     * is created automatically from the number extracted from the PDF header.
     */
    public StatementPreviewResponse preview(Long userId, byte[] pdfBytes) {
        StatementDraft draft = prepare(userId, pdfBytes);
        String draftId = draftStore.put(draft);
        return toPreview(draftId, draft, countDuplicates(draft.transactions()));
    }

    /** Step 2: re-run categorisation over the draft using the current rule set. */
    public StatementPreviewResponse recategorize(Long userId, String draftId) {
        StatementDraft draft = getDraft(userId, draftId);
        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);
        for (Transaction tx : draft.transactions()) {
            if (!tx.isCategoryManual()) {
                tx.setCategoryId(categorizationEngine.categorize(tx, rules));
            }
        }
        return toPreview(draftId, draft, countDuplicates(draft.transactions()));
    }

    /** Step 2: remove one transaction from the draft (it will not be imported). */
    public StatementPreviewResponse removeTransaction(Long userId, String draftId, int rowNumber) {
        StatementDraft draft = getDraft(userId, draftId);
        List<Transaction> kept = draft.transactions().stream()
                .filter(tx -> tx.getRowNumber() == null || tx.getRowNumber() != rowNumber)
                .collect(Collectors.toCollection(ArrayList::new));
        StatementDraft updated = new StatementDraft(draft.userId(), draft.cardId(), draft.parserVersion(),
                draft.periodFrom(), draft.periodTo(), draft.cardNumber(), draft.contract(), draft.account(),
                draft.rowCountRaw(), kept.size(), kept, draft.warnings(), draft.unrecognizedRows(), draft.createdAt());
        draftStore.replace(draftId, updated);
        return toPreview(draftId, updated, countDuplicates(kept));
    }

    /** Step 2: assign a category manually to one transaction in the draft. */
    public StatementPreviewResponse assignDraftCategory(Long userId, String draftId, int rowNumber,
                                                        Long categoryId) {
        StatementDraft draft = getDraft(userId, draftId);
        categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        Transaction tx = draft.transactions().stream()
                .filter(t -> t.getRowNumber() != null && t.getRowNumber() == rowNumber)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Строка не найдена"));
        tx.setCategoryId(categoryId);
        tx.setCategoryManual(true);
        return toPreview(draftId, draft, countDuplicates(draft.transactions()));
    }

    /** Step 3: dedup and persist the draft, then discard it. */
    @Transactional
    public StatementImportResponse confirm(Long userId, String draftId) {
        StatementDraft draft = getDraft(userId, draftId);

        List<String> fingerprints = draft.transactions().stream()
                .map(Transaction::getFingerprint)
                .toList();
        Set<String> existing = fingerprints.isEmpty()
                ? Set.of()
                : transactionRepository.findExistingFingerprints(fingerprints);

        int skippedDuplicates = 0;
        List<Transaction> toSave = new ArrayList<>();
        for (Transaction tx : draft.transactions()) {
            if (existing.contains(tx.getFingerprint())) {
                skippedDuplicates++;
                continue;
            }
            toSave.add(tx);
        }

        String status = draft.unrecognizedRows().isEmpty() ? "OK" : "PARTIAL";

        Statement statement = new Statement(draft.cardId(), draft.periodFrom(), draft.periodTo(),
                draft.parserVersion(), status, draft.validCount(), draft.rowCountRaw(),
                draft.cardNumber(), draft.contract(), draft.account());
        statement = statementRepository.save(statement);

        for (Transaction t : toSave) {
            t.setStatementId(statement.getId());
        }
        transactionRepository.saveAll(toSave);
        statementGapService.detectGaps(draft.cardId());
        draftStore.remove(draftId);

        return new StatementImportResponse(statement.getId(), status, draft.rowCountRaw(),
                toSave.size(), skippedDuplicates, draft.warnings(), draft.unrecognizedRows());
    }

    @Transactional(readOnly = true)
    public List<Statement> listAll(Long userId) {
        return statementRepository.findByUserIdOrderByIdDesc(userId);
    }

    private StatementDraft prepare(Long userId, byte[] pdfBytes) {
        ParsedStatement parsed;
        try {
            parsed = parser.parse(pdfBytes);
        } catch (StatementParseException e) {
            throw new BadRequestException("Не удалось распознать выписку: " + e.getMessage());
        }

        // The card is created automatically from the extracted card number.
        Card card = cardService.findOrCreateByNumber(userId, parsed.cardNumber());
        Long cardId = card.getId();

        List<String> warnings = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>(parsed.unrecognizedRows());
        List<ParsedRow> validRows = new ArrayList<>();

        int prevRowNumber = 0;
        for (ParsedRow row : parsed.rows()) {
            boolean xorOk = (row.expense() != null) != (row.income() != null);
            boolean positive = row.amount() != null && row.amount().signum() > 0;
            if (!xorOk || !positive) {
                unrecognized.add("Строка " + row.rowNumber() + ": некорректная сумма/колонка");
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

        // Period overlap with an already imported statement (warning only).
        if (parsed.periodFrom() != null && parsed.periodTo() != null
                && statementRepository.existsOverlapping(cardId, parsed.periodFrom(), parsed.periodTo())) {
            warnings.add("Период выписки пересекается с уже загруженной выпиской");
        }

        // Classify rows with the current rule set.
        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);

        List<Transaction> transactions = new ArrayList<>();
        for (ParsedRow row : validRows) {
            String fp = fingerprint(userId, cardId, row);
            Transaction tx = new Transaction(userId, cardId, row.rowNumber(), row.date(), row.operation(),
                    row.purpose(), row.amount(), row.type(), fp);
            tx.setCategoryId(categorizationEngine.categorize(tx, rules));
            transactions.add(tx);
        }

        return new StatementDraft(userId, cardId, parser.version(), parsed.periodFrom(), parsed.periodTo(),
                parsed.cardNumber(), parsed.contract(), parsed.account(), parsed.rowCountRaw(),
                validRows.size(), transactions, warnings, unrecognized, Instant.now());
    }

    private StatementDraft getDraft(Long userId, String draftId) {
        StatementDraft draft = draftStore.get(draftId);
        if (draft == null || !draft.userId().equals(userId)) {
            throw new NotFoundException("Черновик не найден или устарел");
        }
        return draft;
    }

    private int countDuplicates(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return 0;
        }
        List<String> fingerprints = transactions.stream().map(Transaction::getFingerprint).toList();
        Set<String> existing = transactionRepository.findExistingFingerprints(fingerprints);
        return (int) transactions.stream().filter(t -> existing.contains(t.getFingerprint())).count();
    }

    private StatementPreviewResponse toPreview(String draftId, StatementDraft draft, int skippedDuplicates) {
        Map<Long, String> categoryNames = categoryRepository.findByUserIdOrderByNameAsc(draft.userId()).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        List<StatementPreviewResponse.PreviewTransaction> previewTxns = draft.transactions().stream()
                .map(tx -> new StatementPreviewResponse.PreviewTransaction(
                        tx.getRowNumber() == null ? 0 : tx.getRowNumber(),
                        tx.getDate(), tx.getOperation(), tx.getPurpose(), tx.getAmount(), tx.getType(),
                        tx.getCategoryId(),
                        tx.getCategoryId() == null ? null : categoryNames.get(tx.getCategoryId())))
                .toList();
        return new StatementPreviewResponse(draftId, draft.periodFrom(), draft.periodTo(),
                draft.cardNumber(), draft.contract(), draft.account(), draft.rowCountRaw(),
                draft.validCount(), skippedDuplicates, draft.warnings(), draft.unrecognizedRows(), previewTxns);
    }

    private static String fingerprint(Long userId, Long cardId, ParsedRow row) {
        String purpose = row.purpose() == null ? "" : row.purpose().trim().toLowerCase().replaceAll("\\s+", " ");
        String raw = userId + "|" + cardId + "|" + row.date() + "|" + row.amount().toPlainString()
                + "|" + row.type() + "|" + purpose;
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
