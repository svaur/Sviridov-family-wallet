package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class StatementService {

    private final CardRepository cardRepository;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final StatementParser parser;

    public StatementService(CardRepository cardRepository, StatementRepository statementRepository,
                            TransactionRepository transactionRepository, StatementParser parser) {
        this.cardRepository = cardRepository;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.parser = parser;
    }

    /**
     * Parses and imports a statement PDF for a card. Persists the import fact
     * (statements row) plus the non-duplicate transactions; the PDF bytes are
     * never stored. Returns counts, warnings and unrecognised rows.
     */
    @Transactional
    public StatementImportResponse importStatement(Long userId, Long cardId, byte[] pdfBytes) {
        Card card = cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new NotFoundException("Карта не найдена"));

        ParsedStatement parsed;
        try {
            parsed = parser.parse(pdfBytes);
        } catch (StatementParseException e) {
            throw new BadRequestException("Не удалось распознать выписку: " + e.getMessage());
        }

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

        // Card reconciliation (warning only, does not block the import).
        if (card.getLast4() != null && !card.getLast4().isBlank()
                && parsed.cardNumber() != null && !parsed.cardNumber().isBlank()) {
            String extractedLast4 = last4(parsed.cardNumber());
            if (!card.getLast4().equals(extractedLast4)) {
                warnings.add("Номер карты в выписке (…" + extractedLast4
                        + ") не совпадает с картой (…" + card.getLast4() + ")");
            }
        }

        // Period overlap with an already imported statement (warning only).
        if (parsed.periodFrom() != null && parsed.periodTo() != null
                && statementRepository.existsOverlapping(cardId, parsed.periodFrom(), parsed.periodTo())) {
            warnings.add("Период выписки пересекается с уже загруженной выпиской");
        }

        // Dedup by fingerprint; soft-deleted rows keep their fingerprint so they
        // are not restored (tombstone).
        List<String> fingerprints = validRows.stream()
                .map(r -> fingerprint(userId, cardId, r))
                .toList();
        Set<String> existing = fingerprints.isEmpty()
                ? Set.of()
                : transactionRepository.findExistingFingerprints(fingerprints);

        int skippedDuplicates = 0;
        List<Transaction> toSave = new ArrayList<>();
        for (ParsedRow row : validRows) {
            String fp = fingerprint(userId, cardId, row);
            if (existing.contains(fp)) {
                skippedDuplicates++;
                continue;
            }
            toSave.add(new Transaction(userId, cardId, row.rowNumber(), row.date(), row.operation(),
                    row.purpose(), row.amount(), row.type(), fp));
        }

        String status = unrecognized.isEmpty() ? "OK" : "PARTIAL";

        Statement statement = new Statement(cardId, parsed.periodFrom(), parsed.periodTo(), parser.version(),
                status, validRows.size(), parsed.rowCountRaw(), parsed.cardNumber(), parsed.contract(), parsed.account());
        statement = statementRepository.save(statement);

        for (Transaction t : toSave) {
            t.setStatementId(statement.getId());
        }
        transactionRepository.saveAll(toSave);

        return new StatementImportResponse(statement.getId(), status, parsed.rowCountRaw(),
                toSave.size(), skippedDuplicates, warnings, unrecognized);
    }

    @Transactional(readOnly = true)
    public List<Statement> listForCard(Long userId, Long cardId) {
        cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new NotFoundException("Карта не найдена"));
        return statementRepository.findByCardIdOrderByIdDesc(cardId);
    }

    private static String last4(String cardNumber) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
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
