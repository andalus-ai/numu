package tech.kayys.numou.billing.domain.valueobject;

import java.time.temporal.ChronoUnit;

/**
 * Dunning level configuration.
 */
public record DunningLevel(
        int level,
        String name,
        int daysDelay,
        DunningAction action,
        String messageTemplate,
        int retryCount,
        ChronoUnit retryInterval
) {
    public DunningLevel {
        if (level < 1) {
            throw new IllegalArgumentException("Level must be at least 1");
        }
        if (daysDelay < 0) {
            throw new IllegalArgumentException("Days delay cannot be negative");
        }
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
    }

    public static DunningLevel create(
            int level,
            String name,
            int daysDelay,
            DunningAction action,
            String messageTemplate) {
        return new DunningLevel(
            level,
            name,
            daysDelay,
            action,
            messageTemplate,
            1,
            ChronoUnit.DAYS
        );
    }

    public static DunningLevel withRetry(
            int level,
            String name,
            int daysDelay,
            DunningAction action,
            String messageTemplate,
            int retryCount,
            ChronoUnit retryInterval) {
        return new DunningLevel(
            level,
            name,
            daysDelay,
            action,
            messageTemplate,
            retryCount,
            retryInterval
        );
    }
}