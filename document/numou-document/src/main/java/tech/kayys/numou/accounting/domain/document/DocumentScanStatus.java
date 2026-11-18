package tech.kayys.numou.accounting.domain.document;

/** Security decision for one immutable document version. */
public enum DocumentScanStatus {
    PENDING,
    CLEAN,
    INFECTED,
    FAILED
}
