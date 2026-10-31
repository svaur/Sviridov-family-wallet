package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @InjectMocks
    private CardService cardService;

    @Test
    void findOrCreateByNumberCreatesWhenAbsent() {
        when(cardRepository.findByUserIdAndCardNumber(7L, "2202XXXXXXXX1234")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card card = cardService.findOrCreateByNumber(7L, "2202XXXXXXXX1234");

        assertThat(card.getUserId()).isEqualTo(7L);
        assertThat(card.getCardNumber()).isEqualTo("2202XXXXXXXX1234");
        assertThat(card.getLast4()).isEqualTo("1234");
        assertThat(card.getDisplayName()).isEqualTo("Карта ****1234");
        assertThat(card.getCurrency()).isEqualTo("RUB");
        assertThat(card.isArchived()).isFalse();
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void findOrCreateByNumberReturnsExisting() {
        Card existing = new Card(7L, "2202XXXXXXXX1234", "Зарплатная");
        when(cardRepository.findByUserIdAndCardNumber(7L, "2202XXXXXXXX1234")).thenReturn(Optional.of(existing));

        Card card = cardService.findOrCreateByNumber(7L, "2202XXXXXXXX1234");

        assertThat(card).isSameAs(existing);
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void findOrCreateByNumberRejectsBlankNumber() {
        assertThatThrownBy(() -> cardService.findOrCreateByNumber(7L, "  "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getThrowsNotFoundForUnknownCard() {
        when(cardRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.get(7L, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void renameUpdatesDisplayName() {
        Card card = new Card(7L, "2202XXXXXXXX1234", "Карта ****1234");
        when(cardRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = cardService.rename(7L, 5L, "Зарплатная");

        assertThat(result.getDisplayName()).isEqualTo("Зарплатная");
    }
}
