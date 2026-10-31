package ru.sviridov.wallet.web.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sviridov.wallet.domain.Rule;

import java.util.Map;

public record RuleResponse(Long id, String name, Map<String, Object> params, Long categoryId,
                           int priority, boolean autoLearned) {

    public static RuleResponse from(Rule rule, ObjectMapper objectMapper) {
        Map<String, Object> params = Map.of();
        if (rule.getParams() != null) {
            try {
                params = objectMapper.readValue(rule.getParams(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                params = Map.of();
            }
        }
        return new RuleResponse(rule.getId(), rule.getName(), params, rule.getCategoryId(),
                rule.getPriority(), rule.isAutoLearned());
    }
}
