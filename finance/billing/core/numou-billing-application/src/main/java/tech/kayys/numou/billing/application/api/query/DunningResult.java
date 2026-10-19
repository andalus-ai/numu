package tech.kayys.numou.billing.application.api.query;

public record DunningResult(
        int totalProcessed,
        int successful,
        int failed,
        String message
) {}
