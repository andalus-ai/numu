package tech.kayys.numou.security.application.problem;

import java.util.Map;

/**
 * RFC 7807-style error body. {@code code} is a stable machine-readable identifier clients can switch on;
 * {@code detail} is safe to show a user and never contains stack traces, SQL or internal class names.
 */
public record Problem(int status, String code, String title, String detail, Map<String, Object> extensions) {

    public Problem {
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }
}
