package tech.kayys.syirkah.observability;

import io.smallrye.mutiny.Uni;

/**
 * Health evaluator contract (config02.md §P4-07 #11).
 */
public interface HealthEvaluator {

    Uni<HealthReport> evaluate();
}
