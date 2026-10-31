package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A categorization rule owned by a user (or auto-learned). Applied in priority
 * order (descending), first match wins. {@code params} is a flat JSON object
 * (jsonb) of optional condition keys; a rule matches iff EVERY present condition
 * matches (implicit AND). Supported keys: {@code merchant_contains},
 * {@code merchant_regex}, {@code min}, {@code max}, {@code day_from},
 * {@code day_to}.
 */
@Entity
@Table(name = "rules")
public class Rule {

    public static final String KEY_MERCHANT_CONTAINS = "merchant_contains";
    public static final String KEY_MERCHANT_REGEX = "merchant_regex";
    public static final String KEY_MIN = "min";
    public static final String KEY_MAX = "max";
    public static final String KEY_DAY_FROM = "day_from";
    public static final String KEY_DAY_TO = "day_to";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Flat JSON object of optional conditions; stored as jsonb. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String params;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "auto_learned", nullable = false)
    private boolean autoLearned;

    protected Rule() {
    }

    public Rule(Long userId, String params, Long categoryId, Integer priority, boolean autoLearned) {
        this.userId = userId;
        this.params = params;
        this.categoryId = categoryId;
        this.priority = priority == null ? 0 : priority;
        this.autoLearned = autoLearned;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority == null ? 0 : priority;
    }

    public boolean isAutoLearned() {
        return autoLearned;
    }
}
