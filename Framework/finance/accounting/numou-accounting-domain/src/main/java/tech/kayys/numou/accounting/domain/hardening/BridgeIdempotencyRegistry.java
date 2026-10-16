package tech.kayys.syirkah.accounting.domain.hardening;

public interface BridgeIdempotencyRegistry {
    boolean alreadyDispatched(String sourceEventId, String bridgeCode);
    void record(String sourceEventId, String bridgeCode, String targetCommand);
}
