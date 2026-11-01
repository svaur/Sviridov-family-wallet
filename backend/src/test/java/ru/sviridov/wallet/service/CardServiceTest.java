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
        when(cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(7L, "1234")).thenReturn(Optional.empty());
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
        when(cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(7L, "1234")).thenReturn(Optional.of(existing));

        Card card = cardService.findOrCreateByNumber(7L, "2202XXXXXXXX1234");

        assertThat(card).isSameAs(existing);
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void findOrCreateByNumberMatchesScreenshotMaskByLast4() {
        // A screenshot yields a masked "••••4242"; it must resolve to the same
        // card as a full "2202XXXXXXXX4242" (matched by last4).
        Card existing = new Card(7L, "2202XXXXXXXX4242", "Основная");
        when(cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(7L, "4242")).thenReturn(Optional.of(existing));

        Card card = cardService.findOrCreateByNumber(7L, "••••4242");

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
    void createSavesCard() {
        when(cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(7L, "1234")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card card = cardService.create(7L, "2202XXXXXXXX1234", "Основная");

        assertThat(card.getCardNumber()).isEqualTo("2202XXXXXXXX1234");
        assertThat(card.getLast4()).isEqualTo("1234");
        assertThat(card.getDisplayName()).isEqualTo("Основная");
    }

    @Test
    void createRejectsDuplicateLast4() {
        when(cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(7L, "1234"))
                .thenReturn(Optional.of(new Card(7L, "2202XXXXXXXX1234", "Старая")));

        assertThatThrownBy(() -> cardService.create(7L, "2202XXXXXXXX1234", "Новая"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("уже существует");
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void updateKeepsNumberWhenBlank() {
        Card card = new Card(7L, "2202XXXXXXXX1234", "Карта ****1234");
        when(cardRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = cardService.update(7L, 5L, null, "Зарплатная");

        assertThat(result.getDisplayName()).isEqualTo("Зарплатная");
        assertThat(result.getCardNumber()).isEqualTo("2202XXXXXXXX1234");
        assertThat(result.getLast4()).isEqualTo("1234");
    }

    @Test
    void updateChangesNumberAndDerivesNewLast4() {
        Card card = new Card(7L, "2202XXXXXXXX1234", "Основная");
        when(cardRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(card));
        when(cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(7L, "4242")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = cardService.update(7L, 5L, "2202XXXXXXXX4242", "Основная");

        assertThat(result.getCardNumber()).isEqualTo("2202XXXXXXXX4242");
        assertThat(result.getLast4()).isEqualTo("4242");
    }

    @Test
    void updateRejectsNumberOwnedByAnotherCard() {
        Card card = new Card(7L, "2202XXXXXXXX1234", "Основная");
        when(cardRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(card));
        when(cardRepository.findFirstByUserIdAndLast4OrderByIdAsc(7L, "4242"))
                .thenReturn(Optional.of(new Card(7L, "2202XXXXXXXX4242", "Другая")));

        assertThatThrownBy(() -> cardService.update(7L, 5L, "2202XXXXXXXX4242", "Основная"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("4242");
    }

    @Test
    void deleteRemovesCard() {
        Card card = new Card(7L, "2202XXXXXXXX1234", "Основная");
        when(cardRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(card));

        cardService.delete(7L, 5L);

        verify(cardRepository).delete(card);
    }
}
