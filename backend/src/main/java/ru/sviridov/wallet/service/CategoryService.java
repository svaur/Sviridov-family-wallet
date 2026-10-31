package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.web.NotFoundException;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final DefaultDataSeeder defaultDataSeeder;
    private final TransactionService transactionService;

    public CategoryService(CategoryRepository categoryRepository, DefaultDataSeeder defaultDataSeeder,
                           TransactionService transactionService) {
        this.categoryRepository = categoryRepository;
        this.defaultDataSeeder = defaultDataSeeder;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public List<Category> list(Long userId) {
        defaultDataSeeder.ensureDefaults(userId);
        return categoryRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public Category create(Long userId, String name, Long parentId) {
        defaultDataSeeder.ensureDefaults(userId);
        validateParent(userId, parentId);
        return categoryRepository.save(new Category(userId, parentId, name));
    }

    @Transactional
    public Category update(Long userId, Long id, String name, Long parentId) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        validateParent(userId, parentId);
        category.setName(name);
        category.setParentId(parentId);
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
}
