package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;

import java.util.List;
import java.util.Set;

/**
 * Categories are strictly per-user. The only seeded category is the system
 * "исключено" (see {@link #ensureExcludedCategory}), whose rows are dropped from
 * analytics; everything else the user creates themselves. {@code parentId}
 * exists for a future hierarchy; it is validated but not used for aggregation
 * yet.
 *
 * <p>The icon is an emoji from a fixed allow-list. A closed list (rather than
 * arbitrary text) keeps the chips/tables consistent and prevents the icon
 * column from becoming a free-text field.</p>
 *
 * <p>Deleting a category relies on DB foreign keys: rules pointing at it are
 * removed and transactions keep the row with {@code category_id = null}. We must
 * {@code flush()} before re-categorising, otherwise the cascade has not run yet
 * and transactions would still appear categorised.</p>
 */
@Service
public class CategoryService {

    private static final String DEFAULT_ICON = "📦";

    private static final Set<String> ALLOWED_ICONS = Set.of(
            "🏠", "💡", "🔧", "🛒", "☕",
            "🚗", "⛽", "🚌", "✈️",
            "💊", "⚕️", "🏋️", "💇",
            "🧸", "🎬", "📚", "🎁", "🐾",
            "💰", "🔄", "🧾", "📱", "📦", "🚫");

    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;

    public CategoryService(CategoryRepository categoryRepository, TransactionService transactionService) {
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
    }

    @Transactional
    public List<Category> list(Long userId) {
        ensureExcludedCategory(userId);
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
        if (category.isExcluded()) {
            throw new BadRequestException("Системная категория «исключено» не может быть удалена");
        }
        categoryRepository.delete(category);
        // Execute the DELETE now so the DB-level cascade (rules removed,
        // transactions.category_id set to null) is visible to re-categorisation.
        categoryRepository.flush();
        transactionService.reCategorizeAll(userId);
    }

    /**
     * The "исключено" category is a system default: assigning a transaction to
     * it (by hand or by a rule) removes the transaction from the dashboard. It
     * is created lazily on first list so new users (created after the migration)
     * still get exactly one. There is never more than one per user because
     * {@code is_excluded} is not settable through the public API.
     */
    private void ensureExcludedCategory(Long userId) {
        if (!categoryRepository.existsByUserIdAndIsExcludedTrue(userId)) {
            categoryRepository.save(new Category(userId, null, "исключено", "🚫", true));
        }
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
