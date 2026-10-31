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
import ru.sviridov.wallet.service.CategoryService;
import ru.sviridov.wallet.web.dto.CategoryRequest;
import ru.sviridov.wallet.web.dto.CategoryResponse;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AppUser user) {
        return categoryService.list(user.getId()).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal AppUser user,
                                                   @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = CategoryResponse.from(
                categoryService.create(user.getId(), request.name(), request.parentId(), request.icon()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal AppUser user,
                                   @PathVariable Long id,
                                   @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(
                categoryService.update(user.getId(), id, request.name(), request.parentId(), request.icon()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        categoryService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
