package tech.kayys.syirkah.accounting.application.hardening;

import tech.kayys.syirkah.accounting.domain.hardening.BridgeDispatch;
import tech.kayys.syirkah.accounting.domain.hardening.BridgeIdempotencyRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBridgeIdempotencyRegistry implements BridgeIdempotencyRegistry {

    private final Map<String, BridgeDispatch> dispatches = new ConcurrentHashMap<>();

    private String key(String sourceEventId, String bridgeCode) {
        return sourceEventId + "::" + bridgeCode;
    }

    @Override
    public boolean alreadyDispatched(String sourceEventId, String bridgeCode) {
        return dispatches.containsKey(key(sourceEventId, bridgeCode));
    }

    @Override
    public void record(String sourceEventId, String bridgeCode, String targetCommand) {
        var dispatch = new BridgeDispatch(
                UUID.randomUUID().toString(),
                sourceEventId,
                bridgeCode,
                targetCommand,
                java.time.Instant.now()
        );
        dispatches.put(key(sourceEventId, bridgeCode), dispatch);
    }

    public int totalDispatches() {
        return dispatches.size();
    }
}
