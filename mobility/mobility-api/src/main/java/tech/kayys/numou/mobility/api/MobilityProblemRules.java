package tech.kayys.numou.mobility.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import tech.kayys.numou.aggregate.domain.ConcurrencyConflictException;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.mobility.driver.domain.DriverUnavailableException;
import tech.kayys.numou.mobility.ride.application.RideExceptions;
import tech.kayys.numou.mobility.ride.domain.ActiveRideExistsException;
import tech.kayys.numou.mobility.ride.domain.OfferExpiredException;
import tech.kayys.numou.mobility.wallet.domain.InsufficientFundsException;
import tech.kayys.numou.reliability.application.idempotency.DuplicateRequestException;
import tech.kayys.numou.security.adapter.ProblemRuleContributor;
import tech.kayys.numou.security.application.problem.Problem;
import tech.kayys.numou.security.application.problem.ProblemMapper;

import java.util.Map;
import java.util.stream.Collectors;

/** HTTP mappings for ride-hailing failures. Stable {@code code}s let the apps show the right screen. */
@ApplicationScoped
public class MobilityProblemRules implements ProblemRuleContributor {

    @Override
    public void contribute(ProblemMapper m) {
        m.register(NotFoundException.class, t -> new Problem(404, "not-found", "Not found", "The requested resource was not found.", null));
        m.register(ConcurrencyConflictException.class, t -> new Problem(409, "concurrent-update", "Concurrent update", "Please retry.", null));
        m.register(DuplicateRequestException.class, t -> new Problem(409, "duplicate-request", "Request in progress", "An identical request is still being processed.", null));
        m.register(ActiveRideExistsException.class, t -> ProblemMapper.safe(409, "active-ride-exists", "Ride in progress", t));
        m.register(OfferExpiredException.class, t -> ProblemMapper.safe(409, "offer-expired", "Offer no longer available", t));
        m.register(DriverUnavailableException.class, t -> ProblemMapper.safe(409, "driver-unavailable", "Driver unavailable", t));
        m.register(InsufficientFundsException.class, t -> new Problem(402, "insufficient-funds", "Insufficient balance", "Your wallet balance is too low.", null));
        m.register(RideExceptions.OutsideServiceAreaException.class, t -> ProblemMapper.safe(422, "outside-service-area", "Outside service area", t));
        m.register(RideExceptions.PinLockedException.class, t -> ProblemMapper.safe(423, "pin-locked", "PIN locked", t));
        m.register(RideExceptions.WrongPinException.class, t -> ProblemMapper.safe(422, "wrong-pin", "Incorrect PIN", t,
                Map.of("attemptsLeft", ((RideExceptions.WrongPinException) t).attemptsLeft())));
        m.register(tech.kayys.numou.mobility.wallet.application.payment.InvalidSignatureException.class,
                t -> new Problem(401, "invalid-signature", "Unauthorized", "Invalid signature.", null));
        m.register(ConstraintViolationException.class, t -> new Problem(400, "validation-failed", "Validation failed",
                ((ConstraintViolationException) t).getConstraintViolations().stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted()
                        .collect(Collectors.joining("; ")), null));
    }
}
