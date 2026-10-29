package tech.kayys.numou.queuemanagement.spi;

/** Queue-owned transition performed atomically with selecting the next waiting entry. */
public enum ClaimMode {
    CALL_SERVICE_ENTRY,
    OFFER_DISPATCH_ENTRY
}