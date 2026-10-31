package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;

import java.util.List;
import java.util.Set;

@Service
public class CategoryService {

    private static final String DEFAULT_ICON = "📦";

    private static final Set<String> ALLOWED_ICONS = Set.of(
            "🏠", "💡", "🔧", "🛒", "☕",
            "🚗", "⛽", "🚌", "✈️",
            "💊", "⚕️", "🏋️", "💇",
            "🧸", "🎬", "📚", "🎁", "🐾",
            "💰", "🔄", "🧾", "📱", "📦");

    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;

    public CategoryService(CategoryRepository categoryRepository, TransactionService transactionService) {
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public List<Category> list(Long userId) {
        return categoryRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public Category create(Long userId, String name, Long parentId, String icon) {
        validateParent(userId, parentId);
        return categoryRepository.save(new Category(userId, parentId, name, resolveIcon(icon)));
    }

    @Transactional
    public Category update(Long userId, Long id, String name, Long parentId, String icon) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        validateParent(userId, parentId);
        category.setName(name);
        category.setParentId(parentId);
        category.setIcon(resolveIcon(icon));
        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        categoryRepository.delete(category);
        // Execute the DELETE now so the DB-level cascade (rules removed,
        // transactions.category_id set to null) is visible to re-categorisation.
        categoryRepository.flush();
        transactionService.reCategorizeAll(userId);
    }

    private void validateParent(Long userId, Long parentId) {
        if (parentId != null && categoryRepository.findByIdAndUserId(parentId, userId).isEmpty()) {
            throw new NotFoundException("Родительская категория не найдена");
        }
    }

    private String resolveIcon(String icon) {
        if (icon == null || icon.isBlank()) {
            return DEFAULT_ICON;
        }
        String trimmed = icon.trim();
        if (!ALLOWED_ICONS.contains(trimmed)) {
            throw new BadRequestException("Недопустимый значок категории");
        }
        return trimmed;
    }
}
