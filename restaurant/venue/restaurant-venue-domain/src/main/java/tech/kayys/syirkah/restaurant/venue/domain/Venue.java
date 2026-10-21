package tech.kayys.syirkah.restaurant.venue.domain;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.aggregate.domain.VersionedAggregate;
import tech.kayys.syirkah.restaurant.kernel.domain.StationId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.venue.domain.event.VenueCreated;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An outlet: its time zone, currency, tax and service-charge policy, and kitchen stations.
 * Tax rates are configuration, not constants: they change by law and by venue (PPN 11% / service 5% are
 * only the defaults of {@link #indonesianDefaults}).
 */
public final class Venue extends VersionedAggregate<VenueId> {

    private static final long serialVersionUID = 1L;

    public record Snapshot(UUID id, String name, String zone, String currency, int fractionDigits, BigDecimal taxRate,
                           BigDecimal serviceChargeRate, boolean taxOnServiceCharge, List<StationSnapshot> stations, boolean active,
                           Instant createdAt, Instant updatedAt, int version) { }

    public record StationSnapshot(UUID id, String name, int parallelism) { }

    private String name;
    private final ZoneId zone;
    private final String currency;
    private final int fractionDigits;
    private BigDecimal taxRate;
    private BigDecimal serviceChargeRate;
    private boolean taxOnServiceCharge;
    private final List<StationRef> stations = new ArrayList<>();
    private boolean active = true;

    private Venue(VenueId id, String name, ZoneId zone, String currency, int fractionDigits, BigDecimal tax, BigDecimal service,
                  boolean taxOnService, Instant now) {
        super(id, now);
        this.name = requireName(name);
        this.zone = Objects.requireNonNull(zone, "zone cannot be null");
        this.currency = Objects.requireNonNull(currency, "currency cannot be null");
        this.fractionDigits = fractionDigits;
        this.taxRate = rate(tax, "tax");
        this.serviceChargeRate = rate(service, "service charge");
        this.taxOnServiceCharge = taxOnService;
    }

    public static Venue create(VenueId id, String name, ZoneId zone, String currency, int fractionDigits, BigDecimal taxRate,
                               BigDecimal serviceChargeRate, boolean taxOnServiceCharge, Instant now) {
        final var v = new Venue(id, name, zone, currency, fractionDigits, taxRate, serviceChargeRate, taxOnServiceCharge, now);
        v.raise(VenueCreated.at(now, id.value(), v.name));
        return v;
    }

    /** Jakarta, IDR (no decimals), PPN 11% charged on food plus a 5% service charge (tax applies on top of it). */
    public static Venue indonesianDefaults(VenueId id, String name, Instant now) {
        return create(id, name, ZoneId.of("Asia/Jakarta"), "IDR", 0, new BigDecimal("0.11"), new BigDecimal("0.05"), true, now);
    }

    public void changeTaxPolicy(BigDecimal tax, BigDecimal service, boolean taxOnService, Instant now) {
        this.taxRate = rate(tax, "tax");
        this.serviceChargeRate = rate(service, "service charge");
        this.taxOnServiceCharge = taxOnService;
        touch(now);
    }

    public void rename(String newName, Instant now) {
        this.name = requireName(newName);
        touch(now);
    }

    public StationRef addStation(String stationName, int parallelism, Instant now) {
        if (stations.stream().anyMatch(s -> s.name().equalsIgnoreCase(stationName))) {
            throw new BusinessRuleViolation("A station named '" + stationName + "' already exists");
        }
        final var s = new StationRef(StationId.generate(), stationName, parallelism);
        stations.add(s);
        touch(now);
        return s;
    }

    public void deactivate(Instant now) {
        this.active = false;
        touch(now);
    }

    private static String requireName(String n) {
        if (n == null || n.isBlank() || n.length() > 120) {
            throw new IllegalArgumentException("Venue name must be 1-120 characters");
        }
        return n.trim();
    }

    private static BigDecimal rate(BigDecimal r, String what) {
        Objects.requireNonNull(r, what + " rate cannot be null");
        if (r.signum() < 0 || r.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(what + " rate must be between 0 and 1");
        }
        return r;
    }

    public String name() { return name; }
    public ZoneId zone() { return zone; }
    public String currency() { return currency; }
    public int fractionDigits() { return fractionDigits; }
    public BigDecimal taxRate() { return taxRate; }
    public BigDecimal serviceChargeRate() { return serviceChargeRate; }
    public boolean taxOnServiceCharge() { return taxOnServiceCharge; }
    public List<StationRef> stations() { return List.copyOf(stations); }
    public boolean active() { return active; }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), name, zone.getId(), currency, fractionDigits, taxRate, serviceChargeRate, taxOnServiceCharge,
                stations.stream().map(s -> new StationSnapshot(s.id().value(), s.name(), s.parallelism())).toList(), active,
                createdAt, updatedAt, version);
    }

    public static Venue restore(Snapshot s) {
        final var v = new Venue(VenueId.of(s.id()), s.name(), ZoneId.of(s.zone()), s.currency(), s.fractionDigits(), s.taxRate(),
                s.serviceChargeRate(), s.taxOnServiceCharge(), s.createdAt());
        s.stations().forEach(st -> v.stations.add(new StationRef(StationId.of(st.id()), st.name(), st.parallelism())));
        v.active = s.active();
        v.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return v;
    }
}
