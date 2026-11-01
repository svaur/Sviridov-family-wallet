package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;

import java.util.List;

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
     * Finds the user's card by the last four digits extracted from the number
     * found in a statement or screenshot, or creates it if it does not exist
     * yet. This is the only way cards come into existence — there is no manual
     * card CRUD. Matching is by {@code last4} so a PDF statement and a
     * screenshot of the same card resolve to the same card.
     */
    @Transactional
    public Card findOrCreateByNumber(Long userId, String cardNumber) {
        String number = cardNumber == null ? null : cardNumber.trim();
        String last4 = last4(number);
        if (last4 == null) {
            throw new BadRequestException("В выписке не найден номер карты");
        }
        return cardRepository.findByUserIdAndLast4(userId, last4)
                .orElseGet(() -> cardRepository.save(new Card(userId, number, null)));
    }

    /**
     * Like {@link #findOrCreateByNumber} but returns {@code null} instead of
     * throwing when the number is missing or has fewer than four digits. Used at
     * preview time so a statement with an undetected card number can still be
     * previewed; the user supplies the number at confirm time.
     */
    @Transactional
    public Card findOrCreateByNumberOrNull(Long userId, String cardNumber) {
        String number = cardNumber == null ? null : cardNumber.trim();
        String last4 = last4(number);
        if (last4 == null) {
            return null;
        }
        return cardRepository.findByUserIdAndLast4(userId, last4)
                .orElseGet(() -> cardRepository.save(new Card(userId, number, null)));
    }

    private static String last4(String number) {
        if (number == null) {
            return null;
        }
        String digits = number.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
    }

    @Transactional
    public Card rename(Long userId, Long cardId, String displayName) {
        Card card = get(userId, cardId);
        card.setDisplayName(displayName);
        return cardRepository.save(card);
    }
}
