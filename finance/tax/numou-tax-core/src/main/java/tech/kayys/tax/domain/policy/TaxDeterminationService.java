package tech.kayys.tax.domain.policy;

import tech.kayys.tax.domain.calculation.TaxDeterminationContext;

import java.util.Optional;

/**
 * Service port for determining applicable tax policy.
 */
public interface TaxDeterminationService {

    /**
     * Resolves the tax policy matching the given context.
     */
    Optional<TaxPolicy> determinePolicy(TaxDeterminationContext context);
}
