package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.Objects;

/**
 * Branch identity on the promotion evaluation boundary.
 *
 * <p>Optional: a promotion may apply to all branches or a specific subset.
 * The resolver uses this to narrow candidates (product04.md §21).</p>
 */
public record BranchId(String value) {

    public BranchId {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static BranchId of(String value) {
        return new BranchId(value);
    }
}
