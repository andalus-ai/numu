package tech.kayys.numou.transport.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One dated instance of a published route. Inventory allocation and ticketing are intentionally
 * separate concerns and are not implied by publishing this schedule.
 */
public final class ScheduledDeparture {
    private static final Pattern SERVICE_CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,39}");

    private final UUID id;
    private final String tenantId;
    private final UUID routeId;
    private final String serviceCode;
    private final List<ScheduledStopTime> stopTimes;
    private final int passengerCapacity;
    private final ScheduledDepartureStatus status;

    private ScheduledDeparture(UUID id, String tenantId, UUID routeId, String serviceCode,
                               List<ScheduledStopTime> stopTimes, int passengerCapacity,
                               ScheduledDepartureStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = requireText(tenantId, "tenantId");
        this.routeId = Objects.requireNonNull(routeId, "routeId");
        this.serviceCode = normalizeCode(serviceCode);
        this.stopTimes = validateStopTimes(stopTimes);
        if (passengerCapacity < 1) {
            throw new IllegalArgumentException("passengerCapacity must be positive");
        }
        this.passengerCapacity = passengerCapacity;
        this.status = Objects.requireNonNull(status, "status");
    }

    public static ScheduledDeparture draft(UUID id, TransportRoute route, String serviceCode,
                                           List<ScheduledStopTime> stopTimes, int passengerCapacity) {
        Objects.requireNonNull(route, "route");
        if (route.status() != RouteStatus.PUBLISHED) {
            throw new IllegalArgumentException("a departure can only be created for a published route");
        }
        List<ScheduledStopTime> validated = validateStopTimes(stopTimes);
        if (validated.size() != route.stops().size()) {
            throw new IllegalArgumentException("scheduled stop times must cover every route stop");
        }
        for (int i = 0; i < route.stops().size(); i++) {
            RouteStop routeStop = route.stops().get(i);
            ScheduledStopTime scheduledStop = validated.get(i);
            if (routeStop.sequence() != scheduledStop.sequence()
                    || !routeStop.stopId().equals(scheduledStop.stopId())) {
                throw new IllegalArgumentException("scheduled stop times must match the route's ordered stops");
            }
        }
        return new ScheduledDeparture(id, route.tenantId(), route.id(), serviceCode,
                validated, passengerCapacity, ScheduledDepartureStatus.DRAFT);
    }

    public ScheduledDeparture publish() {
        if (status != ScheduledDepartureStatus.DRAFT) {
            throw new IllegalStateException("only a draft departure can be published");
        }
        return copyWithStatus(ScheduledDepartureStatus.PUBLISHED);
    }

    public ScheduledDeparture cancel() {
        if (status != ScheduledDepartureStatus.PUBLISHED) {
            throw new IllegalStateException("only a published departure can be cancelled");
        }
        return copyWithStatus(ScheduledDepartureStatus.CANCELLED);
    }

    private ScheduledDeparture copyWithStatus(ScheduledDepartureStatus nextStatus) {
        return new ScheduledDeparture(id, tenantId, routeId, serviceCode, stopTimes, passengerCapacity, nextStatus);
    }

    private static List<ScheduledStopTime> validateStopTimes(List<ScheduledStopTime> values) {
        Objects.requireNonNull(values, "stopTimes");
        if (values.size() < 2) {
            throw new IllegalArgumentException("a scheduled departure must include at least two stops");
        }
        List<ScheduledStopTime> ordered = new ArrayList<>(values);
        ordered.sort(java.util.Comparator.comparingInt(ScheduledStopTime::sequence));
        Instant previousDeparture = null;
        for (int i = 0; i < ordered.size(); i++) {
            ScheduledStopTime current = Objects.requireNonNull(ordered.get(i), "stopTime");
            if (current.sequence() != i + 1) {
                throw new IllegalArgumentException("stop-time sequences must be unique and contiguous starting at 1");
            }
            Instant currentArrival = current.arrivalAt() != null ? current.arrivalAt() : current.departureAt();
            if (previousDeparture != null && currentArrival.isBefore(previousDeparture)) {
                throw new IllegalArgumentException("scheduled stop times must be chronological");
            }
            previousDeparture = current.departureAt() != null ? current.departureAt() : currentArrival;
        }
        return List.copyOf(ordered);
    }

    private static String normalizeCode(String value) {
        String normalized = requireText(value, "serviceCode").toUpperCase(Locale.ROOT);
        if (!SERVICE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("serviceCode must contain 1-40 letters, digits, underscores, or hyphens");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public UUID routeId() { return routeId; }
    public String serviceCode() { return serviceCode; }
    public List<ScheduledStopTime> stopTimes() { return stopTimes; }
    public int passengerCapacity() { return passengerCapacity; }
    public ScheduledDepartureStatus status() { return status; }
}
