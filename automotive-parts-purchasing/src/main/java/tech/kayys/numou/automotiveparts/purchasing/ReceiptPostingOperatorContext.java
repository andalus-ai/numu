package tech.kayys.numou.automotiveparts.purchasing;

/** Tenant and actor resolved by the host from its authenticated request context. */
public record ReceiptPostingOperatorContext(String tenantId, String actorId) {
    public ReceiptPostingOperatorContext {
        require(tenantId, "tenantId");
        require(actorId, "actorId");
    }
    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
