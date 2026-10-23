package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Aggregate for version lineage; each version itself is immutable. */
public final class SyirkahContract {
    private final String contractId;
    private final SyirkahType type;
    private final List<SyirkahContractVersion> versions = new ArrayList<>();

    public SyirkahContract(String contractId, SyirkahType type) {
        this.contractId = SyirkahPartyTerms.requireText(contractId, "contractId");
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    public synchronized void addApprovedVersion(SyirkahContractVersion version) {
        Objects.requireNonNull(version, "version must not be null");
        if (!contractId.equals(version.contractId()) || type != version.type()) {
            throw new IllegalArgumentException("Contract version identity/type does not match aggregate");
        }
        int expected = versions.stream().mapToInt(SyirkahContractVersion::versionNumber).max().orElse(0) + 1;
        if (version.versionNumber() != expected) {
            throw new IllegalArgumentException("Expected contract version " + expected + ", got " + version.versionNumber());
        }
        if (!versions.isEmpty()) {
            SyirkahContractVersion latest = versions.stream().max(Comparator.comparingInt(SyirkahContractVersion::versionNumber)).orElseThrow();
            if (!version.effectiveFrom().isAfter(latest.effectiveFrom())) {
                throw new IllegalArgumentException("New contract versions must become effective after prior versions");
            }
        }
        for (SyirkahContractVersion existing : versions) {
            if (overlaps(existing, version)) {
                throw new IllegalArgumentException("Contract version effective periods overlap: v" + existing.versionNumber() + " and v" + version.versionNumber());
            }
        }
        versions.add(version);
        versions.sort(Comparator.comparing(SyirkahContractVersion::effectiveFrom));
    }

    public synchronized SyirkahContractVersion effectiveVersion(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        List<SyirkahContractVersion> matches = versions.stream().filter(v -> v.isEffectiveOn(date)).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(matches.isEmpty()
                    ? "No approved contract version is effective on " + date
                    : "Multiple contract versions are effective on " + date);
        }
        return matches.get(0);
    }

    public synchronized List<SyirkahContractVersion> versions() {
        return List.copyOf(versions);
    }

    public String contractId() { return contractId; }
    public SyirkahType type() { return type; }

    private static boolean overlaps(SyirkahContractVersion a, SyirkahContractVersion b) {
        LocalDate aEnd = a.effectiveUntil() == null ? LocalDate.MAX : a.effectiveUntil();
        LocalDate bEnd = b.effectiveUntil() == null ? LocalDate.MAX : b.effectiveUntil();
        return !a.effectiveFrom().isAfter(bEnd) && !b.effectiveFrom().isAfter(aEnd);
    }
}
