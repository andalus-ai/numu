package tech.kayys.numou.queuemanagement.domain;

public enum QueueEntryStatus {
    WAITING,
    CALLED,
    SERVING,
    COMPLETED,
    NO_SHOW,
    ABANDONED,
    TRANSFERRED
}
