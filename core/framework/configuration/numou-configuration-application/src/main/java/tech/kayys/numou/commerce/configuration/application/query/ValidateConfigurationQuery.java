package tech.kayys.numou.commerce.configuration.application.query;

import tech.kayys.numou.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.numou.foundation.application.query.Query;

import java.util.Objects;

/**
 * Read-intent message: validate a transient customer selection.
 * Never changes state — served by a stateless {@code QueryHandler}.
 */
public record ValidateConfigurationQuery(
        ProductConfiguration configuration
) implements Query {

    public ValidateConfigurationQuery {
        Objects.requireNonNull(configuration, "configuration cannot be null");
    }
}
