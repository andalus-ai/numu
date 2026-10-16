package tech.kayys.numou.workforce.domain.skill;

import java.util.Objects;

/**
 * Open category reference for skills taxonomy (e.g. TECHNICAL, LOGISTICS, SAFETY).
 */
public record SkillCategoryRef(String code) {

    public SkillCategoryRef {
        Objects.requireNonNull(code, "Category code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("Category code cannot be blank");
        }
    }

    public static SkillCategoryRef of(String code) {
        return new SkillCategoryRef(code);
    }
}
