package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A user-owned spending category. Supports one level of nesting via
 * {@code parentId}. {@code transactions.category_id} references it; a null
 * category on a transaction means UNCATEGORIZED.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 8)
    private String icon;

    @Column(name = "is_excluded", nullable = false)
    private boolean isExcluded;

    protected Category() {
    }

    public Category(Long userId, String name) {
        this(userId, null, name, "📦", false);
    }

    public Category(Long userId, Long parentId, String name) {
        this(userId, parentId, name, "📦", false);
    }

    public Category(Long userId, Long parentId, String name, String icon) {
        this(userId, parentId, name, icon, false);
    }

    public Category(Long userId, Long parentId, String name, String icon, boolean isExcluded) {
        this.userId = userId;
        this.parentId = parentId;
        this.name = name;
        this.icon = icon;
        this.isExcluded = isExcluded;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isExcluded() {
        return isExcluded;
    }

    public void setExcluded(boolean excluded) {
        isExcluded = excluded;
    }
}
