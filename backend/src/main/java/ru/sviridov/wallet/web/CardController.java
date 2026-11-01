package ru.sviridov.wallet.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.CardService;
import ru.sviridov.wallet.web.dto.CardRequest;
import ru.sviridov.wallet.web.dto.CardResponse;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public List<CardResponse> list(@AuthenticationPrincipal AppUser user) {
        return cardService.list(user.getId()).stream().map(CardResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CardResponse> create(@AuthenticationPrincipal AppUser user,
                                               @Valid @RequestBody CardRequest request) {
        CardResponse response = CardResponse.from(
                cardService.create(user.getId(), request.cardNumber(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Full edit when {@code cardNumber} is sent, display-name-only rename when it
     * is omitted (the statements page sends just the name).
     */
    @PutMapping("/{id}")
    public CardResponse update(@AuthenticationPrincipal AppUser user,
                               @PathVariable Long id,
                               @Valid @RequestBody CardRequest request) {
        return CardResponse.from(
                cardService.update(user.getId(), id, request.cardNumber(), request.displayName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUser user,
                                       @PathVariable Long id) {
        cardService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
