package tech.kayys.syirkah.security.application.problem;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.DomainException;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.security.application.authz.AccessDeniedException;
import tech.kayys.syirkah.security.application.redaction.PiiRedactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Turns any {@link Throwable} into a safe {@link Problem}.
 *
 * <p>Rules: most specific registered type wins; domain failures (4xx) carry their own crafted message (run through
 * {@link PiiRedactor} in case it echoes input); <b>anything unrecognised is a 500 with a generic message</b> - the real
 * exception is logged server-side against the correlation id, never sent to the client.
 */
public final class ProblemMapper {

    private final Map<Class<? extends Throwable>, Function<Throwable, Problem>> rules = new LinkedHashMap<>();

    private ProblemMapper() { }

    /** Mapper pre-loaded with the foundation/security exception types. */
    public static ProblemMapper defaults() {
        final var m = new ProblemMapper();
        m.register(AccessDeniedException.class, t -> new Problem(403, "forbidden", "Forbidden", "You are not allowed to do that.", null));
        m.register(InvalidStateException.class, t -> safe(409, "invalid-state", "Conflict with current state", t));
        m.register(BusinessRuleViolation.class, t -> safe(422, "business-rule", "Business rule violated", t));
        m.register(DomainException.class, t -> safe(422, "domain-error", "Request cannot be processed", t));
        m.register(IllegalArgumentException.class, t -> safe(400, "invalid-request", "Invalid request", t));
        m.register(NullPointerException.class, t -> new Problem(400, "invalid-request", "Invalid request", "A required value is missing.", null));
        return m;
    }

    /** Adds (or overrides) a rule. A more specific class registered later still beats a general one. */
    public ProblemMapper register(Class<? extends Throwable> type, Function<Throwable, Problem> rule) {
        rules.put(type, rule);
        return this;
    }

    public Problem map(Throwable failure) {
        final Throwable t = unwrap(failure);
        Class<?> bestType = null;
        Function<Throwable, Problem> best = null;
        for (final var e : rules.entrySet()) {
            if (e.getKey().isInstance(t) && (bestType == null || bestType.isAssignableFrom(e.getKey()))) {
                bestType = e.getKey();
                best = e.getValue();
            }
        }
        return best != null ? best.apply(t) : internal();
    }

    public static Problem internal() {
        return new Problem(500, "internal-error", "Internal error", "Something went wrong. Quote the correlation id when reporting it.", null);
    }

    /** Builds a Problem whose detail is the exception's own message, redacted and bounded. */
    public static Problem safe(int status, String code, String title, Throwable t) {
        return safe(status, code, title, t, null);
    }

    public static Problem safe(int status, String code, String title, Throwable t, Map<String, Object> extensions) {
        String msg = Optional.ofNullable(t.getMessage()).orElse(title);
        msg = PiiRedactor.redact(msg);
        if (msg.length() > 200) {
            msg = msg.substring(0, 200) + "...";
        }
        return new Problem(status, code, title, msg, extensions);
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while ((cur instanceof CompletionException || cur instanceof ExecutionException) && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }
}
