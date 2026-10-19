package tech.kayys.numou.syirkah.application.query;

import tech.kayys.numou.syirkah.domain.CloseSnapshot;
import tech.kayys.numou.syirkah.domain.CloseStatus;
import tech.kayys.numou.syirkah.domain.Syirkah;
import tech.kayys.numou.syirkah.domain.SyirkahId;
import tech.kayys.numou.syirkah.domain.SyirkahStatus;
import tech.kayys.numou.syirkah.domain.SyirkahType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Light read model for listings and dashboards. {@code closeCount} and {@code latestCloseEnd} consider only ACTIVE
 * closes (reversed ones were replaced by a correction).
 */
public record SyirkahSummary(SyirkahId id, SyirkahType type, String currency, SyirkahStatus status,
                             int contractVersion, int partnerCount, int closeCount, LocalDate latestCloseEnd,
                             int version, Instant createdAt, Instant updatedAt) {

    public static SyirkahSummary of(Syirkah s) {
        LocalDate latest = null;
        int active = 0;
        for (CloseSnapshot c : s.closes()) {
            if (c.status() == CloseStatus.ACTIVE) {
                active++;
                if (latest == null || c.periodEnd().isAfter(latest)) {
                    latest = c.periodEnd();
                }
            }
        }
        return new SyirkahSummary(s.id(), s.contract().type(), s.contract().currency().code(), s.status(),
                s.contract().version(), s.contract().partners().size(), active, latest, s.getVersion(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
