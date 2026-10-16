package tech.kayys.numou.survey.domain;

/** Business error categories. Inbound adapters map these to transport-level codes (e.g. HTTP status). */
public enum ErrorCode {
    NOT_FOUND, INVALID_STATE, VALIDATION_FAILED, LIMIT_EXCEEDED, CONFLICT, FORBIDDEN, TIME_LIMIT_EXCEEDED
}
