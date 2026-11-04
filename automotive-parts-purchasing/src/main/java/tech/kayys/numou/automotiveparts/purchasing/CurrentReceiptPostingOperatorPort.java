package tech.kayys.numou.automotiveparts.purchasing;

/** Host adapter returns only the authenticated actor and the tenant selected by trusted scope resolution. */
@FunctionalInterface
public interface CurrentReceiptPostingOperatorPort {
    ReceiptPostingOperatorContext current();
}
