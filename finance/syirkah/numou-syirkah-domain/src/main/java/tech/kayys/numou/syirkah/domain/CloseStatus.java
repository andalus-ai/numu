package tech.kayys.numou.syirkah.domain;

public enum CloseStatus {
    /** Effective close. */
    ACTIVE,
    /** Reversed by a correction; superseded by a newer close. */
    REVERSED
}
