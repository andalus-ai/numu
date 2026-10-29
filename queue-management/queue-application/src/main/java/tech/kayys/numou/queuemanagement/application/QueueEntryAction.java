package tech.kayys.numou.queuemanagement.application;

/** Explicitly enumerated queue-owned lifecycle commands; no arbitrary status assignment. */
public enum QueueEntryAction {
    START_SERVING,
    COMPLETE_SERVICE,
    ACCEPT_DISPATCH_OFFER,
    REJECT_DISPATCH_OFFER,
    COMPLETE_DISPATCH,
    RESOLVE_FROM_SOURCE,
    MARK_NO_SHOW,
    ABANDON,
    TRANSFER,
    REQUEUE
}