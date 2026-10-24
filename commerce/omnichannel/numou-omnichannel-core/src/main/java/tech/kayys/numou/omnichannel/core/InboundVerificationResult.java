package tech.kayys.numou.omnichannel.core;

import java.util.Objects;
import java.util.Optional;

/** Safe result of a durable verification transition; never includes payload bytes. */
public record InboundVerificationResult(
        InboundVerificationDisposition disposition,
        Optional<InboundPayloadReference> payloadReference) {
    public InboundVerificationResult {
        Objects.requireNonNull(disposition, "disposition must not be null");
        payloadReference = Objects.requireNonNull(payloadReference, "payloadReference must not be null");
        boolean success = disposition == InboundVerificationDisposition.VERIFIED
                || disposition == InboundVerificationDisposition.ALREADY_VERIFIED;
        if (success != payloadReference.isPresent()) {
            throw new IllegalArgumentException("payload reference must be present only for successful verification");
        }
    }

    public boolean isVerified() {
        return disposition == InboundVerificationDisposition.VERIFIED
                || disposition == InboundVerificationDisposition.ALREADY_VERIFIED;
    }
}
