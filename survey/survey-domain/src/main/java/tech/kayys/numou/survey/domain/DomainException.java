package tech.kayys.numou.survey.domain;

import java.util.List;

public class DomainException extends tech.kayys.numou.foundation.domain.exception.DomainException {
    private final ErrorCode code;
    private final List<String> details;

    public DomainException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public DomainException(ErrorCode code, String message, List<String> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    public ErrorCode code() { return code; }
    public List<String> details() { return details; }

    public static DomainException notFound(String what, Object id) {
        return new DomainException(ErrorCode.NOT_FOUND, what + " not found: " + id);
    }
}
