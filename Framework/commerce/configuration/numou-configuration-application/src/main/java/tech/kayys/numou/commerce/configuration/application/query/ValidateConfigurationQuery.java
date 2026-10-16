package tech.kayys.syirkah.commerce.configuration.application.query;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.foundation.application.query.Query;

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
