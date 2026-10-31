package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.Size;

public record CommentRequest(@Size(max = 500, message = "Комментарий не должен превышать 500 символов") String comment) {
}
