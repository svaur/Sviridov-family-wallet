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
import ru.sviridov.wallet.service.RuleService;
import ru.sviridov.wallet.web.dto.RuleRequest;
import ru.sviridov.wallet.web.dto.RuleResponse;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<RuleResponse> list(@AuthenticationPrincipal AppUser user) {
        return ruleService.list(user.getId());
    }

    @PostMapping
    public ResponseEntity<RuleResponse> create(@AuthenticationPrincipal AppUser user,
                                               @Valid @RequestBody RuleRequest request) {
        RuleResponse response = ruleService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public RuleResponse update(@AuthenticationPrincipal AppUser user,
                               @PathVariable Long id,
                               @Valid @RequestBody RuleRequest request) {
        return ruleService.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        ruleService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
