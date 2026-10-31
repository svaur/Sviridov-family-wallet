package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.Category;

public record CategoryResponse(Long id, Long parentId, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getParentId(), category.getName());
    }
}
