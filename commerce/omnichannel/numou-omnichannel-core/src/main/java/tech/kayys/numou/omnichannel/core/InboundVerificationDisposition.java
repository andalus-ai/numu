package tech.kayys.numou.omnichannel.core;

/** Outcome of an atomic RECEIVED-to-VERIFIED transition. */
public enum InboundVerificationDisposition {
    VERIFIED,
    ALREADY_VERIFIED,
    NOT_FOUND,
    DIGEST_MISMATCH,
    INVALID_STATE,
    CONCURRENT_CHANGE
}
