package tech.kayys.numou.omnichannel.core;

/** Safe operational outcome from one inbound worker poll. */
public enum InboundWorkerDisposition {
    NO_WORK,
    PROCESSED,
    RETRY_SCHEDULED,
    DEAD_LETTERED,
    STALE_LEASE
}
