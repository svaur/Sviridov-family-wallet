package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.CardRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @InjectMocks
    private CardService cardService;

    @Test
    void createDefaultsCurrencyToRubAndSavesWithUserId() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card card = cardService.create(7L, new CardRequest("Зарплатная", "Сбер", "1234", null, null));

        assertThat(card.getUserId()).isEqualTo(7L);
        assertThat(card.getCurrency()).isEqualTo("RUB");
        assertThat(card.isArchived()).isFalse();
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void getThrowsNotFoundForUnknownCard() {
        when(cardRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.get(7L, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void archiveSetsArchivedTrue() {
        Card card = new Card(7L, "Карта", null, "1111", "RUB");
        when(cardRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = cardService.archive(7L, 5L);

        assertThat(result.isArchived()).isTrue();
    }
}
