package tech.kayys.numou.foundation.application.context;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ambient holder and propagation scope for {@link ExecutionContext}.
 */
public final class CurrentExecutionContext {

    private static final ThreadLocal<ExecutionContext> CURRENT = new ThreadLocal<>();

    private CurrentExecutionContext() {}

    public static Optional<ExecutionContext> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static ExecutionContext getOrEmpty() {
        return get().orElseGet(ExecutionContext::empty);
    }

    public static void set(ExecutionContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T supplyWith(ExecutionContext context, Supplier<T> supplier) {
        var previous = CURRENT.get();
        try {
            set(context);
            return supplier.get();
        } finally {
            set(previous);
        }
    }

    public static void runWith(ExecutionContext context, Runnable runnable) {
        var previous = CURRENT.get();
        try {
            set(context);
            runnable.run();
        } finally {
            set(previous);
        }
    }
}
