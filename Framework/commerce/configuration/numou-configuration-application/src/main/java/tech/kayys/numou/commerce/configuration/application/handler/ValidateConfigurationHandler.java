package tech.kayys.syirkah.commerce.configuration.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.configuration.application.query.ValidateConfigurationQuery;
import tech.kayys.syirkah.commerce.configuration.domain.ConfigurationValidationResult;
import tech.kayys.syirkah.commerce.configuration.domain.ConfigurationValidator;
import tech.kayys.syirkah.commerce.configuration.spi.port.SpecificationLookupPort;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/**
 * Stateless validation: resolves the product's specifications, checks
 * the selection against each, and succeeds when at least one
 * specification validates cleanly (spec evolution support).
 */
public final class ValidateConfigurationHandler
        implements QueryHandler<ValidateConfigurationQuery, Result<ConfigurationValidationResult>> {

    private static final ApplicationError NO_SPECIFICATION =
            ApplicationError.of(
                    "SPECIFICATION_NOT_FOUND",
                    "No specification exists for product");

    private final SpecificationLookupPort specifications;

    public ValidateConfigurationHandler(SpecificationLookupPort specifications) {
        this.specifications = Objects.requireNonNull(specifications);
    }

    @Override
    public Uni<Result<ConfigurationValidationResult>> handle(
            ValidateConfigurationQuery query
    ) {
        return Uni.createFrom()
                .completionStage(specifications.findSpecificationsByProduct(
                        query.configuration().productId()))
                .map(specs -> {
                    if (specs.isEmpty()) {
                        return Result.<ConfigurationValidationResult>failure(NO_SPECIFICATION);
                    }
                    for (var spec : specs) {
                        var result = ConfigurationValidator.validate(
                                spec, query.configuration());
                        if (result.isValid()) {
                            return Result.success(result);
                        }
                    }
                    var first = ConfigurationValidator.validate(
                            specs.getFirst(), query.configuration());
                    return Result.success(first);
                });
    }
}
