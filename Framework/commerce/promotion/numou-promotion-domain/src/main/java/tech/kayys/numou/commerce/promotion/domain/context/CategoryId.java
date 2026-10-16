package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.UUID;

/**
 * Category identity on the promotion evaluation boundary.
 *
 * <p>Deliberately not the product aggregate's category identity; promotion
 * evaluation only needs the opaque category reference so the resolver can
 * narrow candidates by category index (product04.md §10).</p>
 */
public record CategoryId(UUID value) {

    public CategoryId {
        if (value == null) {
            throw new IllegalArgumentException("Category id cannot be null");
        }
    }

    public static CategoryId generate() {
        return new CategoryId(UUID.randomUUID());
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }
}
