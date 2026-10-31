package ru.sviridov.wallet.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.CardService;
import ru.sviridov.wallet.web.dto.CardRenameRequest;
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

    @PutMapping("/{id}")
    public CardResponse rename(@AuthenticationPrincipal AppUser user,
                               @PathVariable Long id,
                               @Valid @RequestBody CardRenameRequest request) {
        return CardResponse.from(cardService.rename(user.getId(), id, request.displayName()));
    }
}
