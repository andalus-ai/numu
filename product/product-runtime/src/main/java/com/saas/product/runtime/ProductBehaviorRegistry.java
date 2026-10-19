package com.saas.product.runtime;

import com.saas.product.spi.ProductBehavior;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime registry of all ProductBehavior implementations.
 *
 * CDI discovers all beans implementing {@link ProductBehavior} and indexes
 * them by their context key on first use.
 *
 * New behaviors are added simply by creating a new @ApplicationScoped class
 * implementing ProductBehavior — no registry modification needed.
 */
@ApplicationScoped
public class ProductBehaviorRegistry {

    private final Map<String, ProductBehavior> index = new HashMap<>();
    private volatile boolean initialized = false;

    @Inject
    Instance<ProductBehavior> behaviors;

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    for (ProductBehavior b : behaviors) {
                        String ctx = b.getContext();
                        if (index.containsKey(ctx)) {
                            throw new IllegalStateException(
                                    "Duplicate ProductBehavior for context '" + ctx + "': "
                                            + index.get(ctx).getClass().getName()
                                            + " vs " + b.getClass().getName());
                        }
                        index.put(ctx, b);
                    }
                    initialized = true;
                }
            }
        }
    }

    /**
     * Retrieve the behavior for a given extension context.
     * @throws IllegalArgumentException if no behavior is registered for that context
     */
    public ProductBehavior get(String context) {
        ensureInitialized();
        ProductBehavior b = index.get(context);
        if (b == null) {
            throw new IllegalArgumentException(
                    "No ProductBehavior registered for context '" + context
                    + "'. Available: " + index.keySet());
        }
        return b;
    }

    public Optional<ProductBehavior> find(String context) {
        ensureInitialized();
        return Optional.ofNullable(index.get(context));
    }

    public boolean supports(String context) {
        ensureInitialized();
        return index.containsKey(context);
    }
}
