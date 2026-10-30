package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.CardRequest;

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

    @Transactional
    public Card create(Long userId, CardRequest request) {
        Card card = new Card(userId, request.displayName(), request.bank(), request.last4(), request.currency());
        return cardRepository.save(card);
    }

    @Transactional
    public Card update(Long userId, Long cardId, CardRequest request) {
        Card card = get(userId, cardId);
        card.setDisplayName(request.displayName());
        card.setBank(request.bank());
        card.setLast4(request.last4());
        if (request.currency() != null && !request.currency().isBlank()) {
            card.setCurrency(request.currency().trim().toUpperCase());
        }
        if (request.archived() != null) {
            card.setArchived(request.archived());
        }
        return cardRepository.save(card);
    }

    @Transactional
    public Card archive(Long userId, Long cardId) {
        Card card = get(userId, cardId);
        card.setArchived(true);
        return cardRepository.save(card);
    }
}
