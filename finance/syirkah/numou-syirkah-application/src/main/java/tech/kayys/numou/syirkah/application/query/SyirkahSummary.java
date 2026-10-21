package tech.kayys.numou.numouapplication.query;

import tech.kayys.numou.numoudomain.CloseSnapshot;
import tech.kayys.numou.numoudomain.CloseStatus;
import tech.kayys.numou.numoudomain.Syirkah;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.SyirkahStatus;
import tech.kayys.numou.numoudomain.SyirkahType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Light read model for listings and dashboards. {@code closeCount} and
 * {@code latestCloseEnd} consider only ACTIVE
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
