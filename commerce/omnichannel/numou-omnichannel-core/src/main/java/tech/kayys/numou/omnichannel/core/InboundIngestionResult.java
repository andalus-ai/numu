package tech.kayys.numou.omnichannel.core;

import java.util.Objects;
import java.util.Optional;

/** Result contains no body, signature, secret, or provider header values. */
public record InboundIngestionResult(InboundIngestionDisposition disposition, String reasonCode, Optional<InboundPayloadReference> payloadReference) {
    public InboundIngestionResult {
        Objects.requireNonNull(disposition, "disposition must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        reasonCode = reasonCode.trim();
        if (reasonCode.isEmpty() || reasonCode.length() > 100) throw new IllegalArgumentException("reasonCode must contain 1 to 100 characters");
        payloadReference = Objects.requireNonNull(payloadReference, "payloadReference must not be null");
        if ((disposition == InboundIngestionDisposition.VERIFIED) != payloadReference.isPresent()) throw new IllegalArgumentException("only verified results include a payload reference");
    }
    public boolean isVerified() { return disposition == InboundIngestionDisposition.VERIFIED; }
}
