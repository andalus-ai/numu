package tech.kayys.syirkah.restaurant.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import tech.kayys.syirkah.restaurant.kernel.domain.ConcurrencyConflictException;
import tech.kayys.syirkah.restaurant.kernel.domain.NotFoundException;
import tech.kayys.syirkah.restaurant.reservation.application.SlotUnavailableException;
import tech.kayys.syirkah.reliability.application.idempotency.DuplicateRequestException;
import tech.kayys.syirkah.security.adapter.ProblemRuleContributor;
import tech.kayys.syirkah.security.application.problem.Problem;
import tech.kayys.syirkah.security.application.problem.ProblemMapper;

import java.util.Map;
import java.util.stream.Collectors;

/** HTTP mappings for restaurant failures. */
@ApplicationScoped
public class RestaurantProblemRules implements ProblemRuleContributor {

    @Override
    public void contribute(ProblemMapper m) {
        m.register(NotFoundException.class, t -> new Problem(404, "not-found", "Not found", "The requested resource was not found.", null));
        m.register(ConcurrencyConflictException.class, t -> new Problem(409, "concurrent-update", "Concurrent update",
                "The resource was changed by someone else. Reload and retry.", null));
        m.register(DuplicateRequestException.class, t -> new Problem(409, "duplicate-request", "Request in progress",
                "An identical request is still being processed.", null));
        m.register(SlotUnavailableException.class, t -> ProblemMapper.safe(409, "slot-unavailable", "Slot unavailable", t,
                Map.of("alternatives", ((SlotUnavailableException) t).alternatives().stream().map(Object::toString).toList())));
        m.register(ConstraintViolationException.class, t -> new Problem(400, "validation-failed", "Validation failed",
                // field paths only: never echo the rejected values, which may be personal data
                ((ConstraintViolationException) t).getConstraintViolations().stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining("; ")), null));
    }
}
