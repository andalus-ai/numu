package tech.kayys.numou.omnichannel.core;

/** Outcome of an atomic durable inbox claim. */
public enum InboxClaimDisposition {
    /** This call atomically created the inbox record; processing may proceed. */
    ACCEPTED,
    /** Same scoped event key and same payload fingerprint already exist. */
    DUPLICATE,
    /** Same scoped event key exists with a different payload fingerprint. */
    PAYLOAD_CONFLICT
}