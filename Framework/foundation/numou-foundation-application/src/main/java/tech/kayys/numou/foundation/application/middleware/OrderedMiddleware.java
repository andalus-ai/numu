package tech.kayys.numou.foundation.application.middleware;

/**
 * Interface for middlewares that declare an explicit execution order in a pipeline.
 *
 * <p>Lower values have higher precedence (execute earlier in the chain, wrapping subsequent middlewares).
 */
public interface OrderedMiddleware {

    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    int CONTEXT_ORDER = 10;
    int CORRELATION_ORDER = 20;
    int SECURITY_ORDER = 30;
    int VALIDATION_ORDER = 40;
    int IDEMPOTENCY_ORDER = 50;
    int TRANSACTION_ORDER = 60;
    int METRICS_ORDER = 70;
    int TRACING_ORDER = 80;
    int ERROR_ORDER = 90;

    /**
     * @return execution precedence order
     */
    default int order() {
        return 0;
    }
}
