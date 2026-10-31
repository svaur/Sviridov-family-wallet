package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        Long parentId,
        @Size(max = 8) String icon) {
}
