package tech.kayys.numou.history.source.domain;

/** Outcome of one immutable extraction attempt. */
public enum SourceExtractionStatus {
    SUCCEEDED,
    PARTIAL,
    FAILED
}
