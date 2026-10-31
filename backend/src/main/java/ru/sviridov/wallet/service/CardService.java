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
     * Finds the user's card by the full masked card number extracted from a
     * statement, or creates it if it does not exist yet. This is the only way
     * cards come into existence — there is no manual card CRUD.
     */
    @Transactional
    public Card findOrCreateByNumber(Long userId, String cardNumber) {
        String number = cardNumber == null ? null : cardNumber.trim();
        if (number == null || number.isBlank()) {
            throw new BadRequestException("В выписке не найден номер карты");
        }
        return cardRepository.findByUserIdAndCardNumber(userId, number)
                .orElseGet(() -> cardRepository.save(new Card(userId, number, null)));
    }

    @Transactional
    public Card rename(Long userId, Long cardId, String displayName) {
        Card card = get(userId, cardId);
        card.setDisplayName(displayName);
        return cardRepository.save(card);
    }
}
