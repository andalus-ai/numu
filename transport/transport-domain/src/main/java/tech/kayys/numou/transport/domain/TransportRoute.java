package tech.kayys.numou.transport.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tenant-owned route definition. Stop identities are references to a location capability;
 * this aggregate does not duplicate addresses, coordinates, or geocoding behavior.
 */
public record TransportRoute(
        UUID id,
        String tenantId,
        String code,
        String name,
        List<RouteStop> stops,
        RouteStatus status
) {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,39}");

    public TransportRoute {
        Objects.requireNonNull(id, "id");
        tenantId = requireText(tenantId, "tenantId");
        code = requireText(code, "code").toUpperCase(java.util.Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("code must contain 1-40 letters, digits, underscores, or hyphens");
        }
        name = requireText(name, "name");
        Objects.requireNonNull(stops, "stops");
        Objects.requireNonNull(status, "status");

        if (stops.size() < 2) {
            throw new IllegalArgumentException("a transport route must contain at least two stops");
        }
        List<RouteStop> ordered = new ArrayList<>(stops);
        ordered.sort(java.util.Comparator.comparingInt(RouteStop::sequence));
        Set<UUID> stopIds = new HashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            RouteStop stop = Objects.requireNonNull(ordered.get(i), "stop");
            if (stop.sequence() != i + 1) {
                throw new IllegalArgumentException("route stop sequences must be unique and contiguous starting at 1");
            }
            if (!stopIds.add(stop.stopId())) {
                throw new IllegalArgumentException("a stop may appear only once in a route");
            }
        }
        stops = List.copyOf(ordered);
    }

    public TransportRoute publish() {
        if (status != RouteStatus.DRAFT) {
            throw new IllegalStateException("only a draft route can be published");
        }
        return new TransportRoute(id, tenantId, code, name, stops, RouteStatus.PUBLISHED);
    }

    public TransportRoute retire() {
        if (status != RouteStatus.PUBLISHED) {
            throw new IllegalStateException("only a published route can be retired");
        }
        return new TransportRoute(id, tenantId, code, name, stops, RouteStatus.RETIRED);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
