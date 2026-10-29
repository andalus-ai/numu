package tech.kayys.numou.queuemanagement.domain;

public enum QueueEntryStatus {
    WAITING,
    CALLED,
    SERVING,
    OFFERED,
    ACCEPTED,
    REJECTED,
    COMPLETED,
    NO_SHOW,
    ABANDONED,
    TRANSFERRED
}
