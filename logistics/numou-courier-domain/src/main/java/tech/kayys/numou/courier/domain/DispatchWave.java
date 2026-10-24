package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-owned dispatch wave. Expected parcel references are reconciled against
 * physical load scans before departure. Evidence and exception reasons are
 * references/text only; this aggregate does not store protected media.
 */
public final class DispatchWave {
    private final UUID id;
    private final TenantId tenantId;
    private final String reference;
    private final String originHubReference;
    private final String destinationHubReference;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant departedAt;
    private DispatchWaveStatus status = DispatchWaveStatus.PLANNED;
    private final Set<String> expectedParcels = new LinkedHashSet<>();
    private final Set<String> scannedParcels = new LinkedHashSet<>();
    private final Set<String> unexpectedParcels = new LinkedHashSet<>();
    private final Set<String> duplicateScans = new LinkedHashSet<>();
    private String authorizedExceptionReason;

    private DispatchWave(UUID id, TenantId tenantId, String reference,
                         String originHubReference, String destinationHubReference, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.reference = required(reference, 80, "reference");
        this.originHubReference = required(originHubReference, 160, "originHubReference");
        this.destinationHubReference = required(destinationHubReference, 160, "destinationHubReference");
        if (this.originHubReference.equals(this.destinationHubReference)) {
            throw new IllegalArgumentException("origin and destination hubs must differ");
        }
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static DispatchWave plan(UUID id, TenantId tenantId, String reference,
                                    String originHubReference, String destinationHubReference, Instant now) {
        return new DispatchWave(id, tenantId, reference, originHubReference, destinationHubReference, now);
    }

    public void expectParcel(String parcelReference, Instant now) {
        requireMutable();
        String parcel = required(parcelReference, 160, "parcelReference");
        if (scannedParcels.contains(parcel) || unexpectedParcels.contains(parcel)) {
            throw new IllegalStateException("cannot add a parcel after it has been scanned");
        }
        if (!expectedParcels.add(parcel)) {
            throw new IllegalArgumentException("parcel is already expected in this wave");
        }
        touch(now);
    }

    public void beginLoading(Instant now) {
        if (status != DispatchWaveStatus.PLANNED) throw new IllegalStateException("wave is not planned");
        if (expectedParcels.isEmpty()) throw new IllegalStateException("wave must contain expected parcels");
        touch(now);
        status = DispatchWaveStatus.LOADING;
    }

    /** Records every scan outcome; unexpected and duplicate scans remain visible as discrepancies. */
    public ScanOutcome scanParcel(String parcelReference, Instant now) {
        if (status != DispatchWaveStatus.LOADING) throw new IllegalStateException("wave is not loading");
        String parcel = required(parcelReference, 160, "parcelReference");
        touch(now);
        if (!expectedParcels.contains(parcel)) {
            unexpectedParcels.add(parcel);
            return ScanOutcome.UNEXPECTED;
        }
        if (!scannedParcels.add(parcel)) {
            duplicateScans.add(parcel);
            return ScanOutcome.DUPLICATE;
        }
        return ScanOutcome.ACCEPTED;
    }

    /** Explicitly authorizes unresolved discrepancies; callers must enforce operator permission/audit. */
    public void authorizeDepartureException(String reason, Instant now) {
        if (status != DispatchWaveStatus.LOADING) throw new IllegalStateException("wave is not loading");
        if (missingParcels().isEmpty() && unexpectedParcels.isEmpty() && duplicateScans.isEmpty()) {
            throw new IllegalStateException("no discrepancy exists to authorize");
        }
        authorizedExceptionReason = required(reason, 500, "exceptionReason");
        touch(now);
    }

    public void markReady(Instant now) {
        if (status != DispatchWaveStatus.LOADING) throw new IllegalStateException("wave is not loading");
        boolean clean = missingParcels().isEmpty() && unexpectedParcels.isEmpty() && duplicateScans.isEmpty();
        if (!clean && (authorizedExceptionReason == null || authorizedExceptionReason.isBlank())) {
            throw new IllegalStateException("unresolved load discrepancies prevent readiness");
        }
        touch(now);
        status = DispatchWaveStatus.READY;
    }

    public void depart(Instant now) {
        if (status != DispatchWaveStatus.READY) throw new IllegalStateException("wave is not ready to depart");
        departedAt = monotonic(now);
        updatedAt = departedAt;
        status = DispatchWaveStatus.DEPARTED;
    }

    public void cancel(Instant now) {
        if (status == DispatchWaveStatus.DEPARTED) throw new IllegalStateException("departed wave cannot be cancelled");
        if (status == DispatchWaveStatus.CANCELLED) return;
        touch(now);
        status = DispatchWaveStatus.CANCELLED;
    }

    public Set<String> missingParcels() {
        Set<String> missing = new LinkedHashSet<>(expectedParcels);
        missing.removeAll(scannedParcels);
        return Collections.unmodifiableSet(missing);
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String reference() { return reference; }
    public String originHubReference() { return originHubReference; }
    public String destinationHubReference() { return destinationHubReference; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant departedAt() { return departedAt; }
    public DispatchWaveStatus status() { return status; }
    public Set<String> expectedParcels() { return Collections.unmodifiableSet(expectedParcels); }
    public Set<String> scannedParcels() { return Collections.unmodifiableSet(scannedParcels); }
    public Set<String> unexpectedParcels() { return Collections.unmodifiableSet(unexpectedParcels); }
    public Set<String> duplicateScans() { return Collections.unmodifiableSet(duplicateScans); }
    public String authorizedExceptionReason() { return authorizedExceptionReason; }

    private void requireMutable() {
        if (status != DispatchWaveStatus.PLANNED && status != DispatchWaveStatus.LOADING) {
            throw new IllegalStateException("wave no longer accepts parcel changes");
        }
    }
    private void touch(Instant now) { updatedAt = monotonic(now); }
    private Instant monotonic(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("time must not move backwards");
        return now;
    }
    private static String required(String value, int max, String field) {
        Objects.requireNonNull(value, field);
        String result = value.trim();
        if (result.isEmpty() || result.length() > max) {
            throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
        }
        return result;
    }

    public enum ScanOutcome { ACCEPTED, UNEXPECTED, DUPLICATE }
}
