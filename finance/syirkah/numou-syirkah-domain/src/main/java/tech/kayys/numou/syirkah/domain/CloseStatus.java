package tech.kayys.numou.numoudomain;

public enum CloseStatus {
    /** Effective close. */
    ACTIVE,
    /** Reversed by a correction; superseded by a newer close. */
    REVERSED
}
