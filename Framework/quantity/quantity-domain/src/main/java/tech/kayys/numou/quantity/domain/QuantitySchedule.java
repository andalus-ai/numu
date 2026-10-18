package tech.kayys.numou.quantity.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable collection of quantity-schedule lines.
 *
 * <p>The schedule enforces line identity, code, and ordering uniqueness while
 * keeping commercial rates, currency, revisions, and approvals in the owning
 * business domain. Quantities are aggregated only by their exact normalized
 * unit code; no unit conversion is inferred.</p>
 */
public final class QuantitySchedule {
    private final UUID id;
    private final List<QuantityScheduleLine> lines;

    public QuantitySchedule(UUID id, List<QuantityScheduleLine> lines) {
        this.id = Objects.requireNonNull(id, "Quantity schedule id cannot be null");
        Objects.requireNonNull(lines, "Quantity schedule lines cannot be null");

        List<QuantityScheduleLine> copy = new ArrayList<>(lines.size());
        Set<UUID> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (QuantityScheduleLine line : lines) {
            Objects.requireNonNull(line, "Quantity schedule cannot contain a null line");
            if (!ids.add(line.id())) {
                throw new IllegalArgumentException("Duplicate quantity schedule line id: " + line.id());
            }
            String normalizedCode = line.code().trim().toLowerCase(Locale.ROOT);
            if (!codes.add(normalizedCode)) {
                throw new IllegalArgumentException("Duplicate quantity schedule line code: " + line.code());
            }
            if (!positions.add(line.position())) {
                throw new IllegalArgumentException("Duplicate quantity schedule line position: " + line.position());
            }
            copy.add(line);
        }
        copy.sort(Comparator.comparingInt(QuantityScheduleLine::position));
        this.lines = List.copyOf(copy);
    }

    public static QuantitySchedule empty(UUID id) {
        return new QuantitySchedule(id, List.of());
    }

    public UUID id() {
        return id;
    }

    /** Lines are immutable and returned in ascending position order. */
    public List<QuantityScheduleLine> lines() {
        return lines;
    }

    public QuantitySchedule addLine(QuantityScheduleLine line) {
        Objects.requireNonNull(line, "Quantity schedule line cannot be null");
        List<QuantityScheduleLine> updated = new ArrayList<>(lines);
        updated.add(line);
        return new QuantitySchedule(id, updated);
    }

    public QuantitySchedule removeLine(UUID lineId) {
        Objects.requireNonNull(lineId, "Quantity schedule line id cannot be null");
        List<QuantityScheduleLine> updated = lines.stream()
                .filter(line -> !line.id().equals(lineId))
                .collect(Collectors.toCollection(ArrayList::new));
        if (updated.size() == lines.size()) {
            throw new IllegalArgumentException("Quantity schedule line not found: " + lineId);
        }
        return new QuantitySchedule(id, updated);
    }

    /** Returns the total for one exact unit; absent units have a zero total. */
    public Quantity total(UnitOfMeasure unit) {
        Objects.requireNonNull(unit, "Unit cannot be null");
        BigDecimal total = lines.stream()
                .filter(line -> line.quantity().unit().equals(unit))
                .map(line -> line.quantity().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Quantity(total, unit);
    }

    /** Returns totals grouped by normalized unit code, without cross-unit conversion. */
    public Map<UnitOfMeasure, Quantity> totalsByUnit() {
        Map<UnitOfMeasure, BigDecimal> totals = new TreeMap<>(
                Comparator.comparing(UnitOfMeasure::code));
        for (QuantityScheduleLine line : lines) {
            totals.merge(line.quantity().unit(), line.quantity().value(), BigDecimal::add);
        }
        Map<UnitOfMeasure, Quantity> result = new TreeMap<>(Comparator.comparing(UnitOfMeasure::code));
        totals.forEach((unit, value) -> result.put(unit, new Quantity(value, unit)));
        return java.util.Collections.unmodifiableMap(result);
    }
}
