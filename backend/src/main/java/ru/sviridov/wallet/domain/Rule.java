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
 * order (desc), first match wins. {@code params} is a JSON object (jsonb) whose
 * shape depends on {@code type}: merchant_contains→{value}, merchant_regex→{pattern},
 * amount_range→{min,max}, day_of_month→{day}.
 */
@Entity
@Table(name = "rules")
public class Rule {

    public static final String TYPE_MERCHANT_CONTAINS = "merchant_contains";
    public static final String TYPE_MERCHANT_REGEX = "merchant_regex";
    public static final String TYPE_AMOUNT_RANGE = "amount_range";
    public static final String TYPE_DAY_OF_MONTH = "day_of_month";

    public static final String KEY_VALUE = "value";
    public static final String KEY_PATTERN = "pattern";
    public static final String KEY_MIN = "min";
    public static final String KEY_MAX = "max";
    public static final String KEY_DAY = "day";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String type;

    /** JSON object whose shape depends on {@link #type}; stored as jsonb. */
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

    public Rule(Long userId, String type, String params, Long categoryId, Integer priority, boolean autoLearned) {
        this.userId = userId;
        this.type = type;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
