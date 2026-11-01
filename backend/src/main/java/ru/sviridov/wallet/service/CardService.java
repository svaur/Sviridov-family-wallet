package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Card identity and manual management.
 *
 * <p>Cards are usually derived from the data: a card is created the first time a
 * statement/screenshot with its number is confirmed. On top of that the "Карты"
 * page lets the user add, edit and delete cards explicitly — handy when a
 * screenshot's card number was not recognised, or to fix a typo in the number.</p>
 *
 * <p>Identity is {@code user_id + last4}: the full number is often masked
 * ("2202XXXXXXXX4242" or "••••4242") and the same card may appear in different
 * maskings in a PDF and in a screenshot, so last4 is the stable key. The number
 * is therefore never treated as unique, only its last four digits are.</p>
 */
@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    public List<Card> list(Long userId) {
        return cardRepository.findByUserIdOrderByIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Card get(Long userId, Long cardId) {
        return cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new NotFoundException("Карта не найдена"));
    }

    /**
     * Manual card creation from the "Карты" page. Rejects a number whose last
     * four digits already belong to one of the user's cards, because a second
     * card with the same last4 would make statement matching ambiguous
     * ({@code findOrCreateByNumber} resolves by last4 only).
     */
    @Transactional
    public Card create(Long userId, String cardNumber, String displayName) {
        String number = cardNumber == null ? null : cardNumber.trim();
        String last4 = last4(number);
        if (last4 == null) {
            throw new BadRequestException("В номере карты должно быть хотя бы 4 цифры");
        }
        if (cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(userId, last4).isPresent()) {
            throw new BadRequestException("Карта с номером, оканчивающимся на " + last4 + ", уже существует");
        }
        return cardRepository.save(new Card(userId, number, displayName));
    }

    /**
     * Edits a card. When {@code cardNumber} is blank only the display name is
     * changed — this is what the statements page's inline rename sends.
     */
    @Transactional
    public Card update(Long userId, Long cardId, String cardNumber, String displayName) {
        Card card = get(userId, cardId);
        if (cardNumber != null && !cardNumber.isBlank()) {
            String number = cardNumber.trim();
            String last4 = last4(number);
            if (last4 == null) {
                throw new BadRequestException("В номере карты должно быть хотя бы 4 цифры");
            }
            cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(userId, last4)
                    .filter(other -> !cardId.equals(other.getId()))
                    .ifPresent(other -> {
                        throw new BadRequestException(
                                "Другая карта уже оканчивается на " + last4);
                    });
            card.setCardNumber(number);
        }
        card.setDisplayName(displayName);
        return cardRepository.save(card);
    }

    /**
     * Deletes a card and, through the schema's {@code ON DELETE CASCADE} FKs,
     * every statement, transaction and statement gap that belongs to it. This is
     * a hard delete: re-importing the same statement afterwards works (the
     * fingerprint tombstones are gone with the rows).
     */
    @Transactional
    public void delete(Long userId, Long cardId) {
        Card card = get(userId, cardId);
        cardRepository.delete(card);
    }

    /**
     * Finds the user's card by the last four digits extracted from the number
     * found in a statement or screenshot, or creates it if it does not exist
     * yet. Matching is by {@code last4} so a PDF statement and a screenshot of
     * the same card resolve to the same card.
     */
    @Transactional
    public Card findOrCreateByNumber(Long userId, String cardNumber) {
        String number = cardNumber == null ? null : cardNumber.trim();
        String last4 = last4(number);
        if (last4 == null) {
            throw new BadRequestException("В выписке не найден номер карты");
        }
        return cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(userId, last4)
                .orElseGet(() -> cardRepository.save(new Card(userId, number, null)));
    }

    /**
     * Read-only lookup by last four digits — used at preview time to check for
     * an overlapping period without creating the card (creation happens on
     * confirm). Returns empty when the number is missing or has fewer than four
     * digits.
     */
    @Transactional(readOnly = true)
    public Optional<Card> findByLast4(Long userId, String cardNumber) {
        String l4 = last4(cardNumber == null ? null : cardNumber.trim());
        return l4 == null ? Optional.empty() : cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(userId, l4);
    }

    private static String last4(String number) {
        if (number == null) {
            return null;
        }
        String digits = number.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
    }
}
