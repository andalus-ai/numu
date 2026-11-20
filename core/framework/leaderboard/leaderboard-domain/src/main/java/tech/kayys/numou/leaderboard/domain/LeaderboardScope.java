package tech.kayys.numou.leaderboard.domain;

import java.util.Objects;

/**
 * Mandatory tenant boundary for a leaderboard. Cross-tenant/global ranking
 * requires an explicit future capability and is not represented by null.
 */
public record LeaderboardScope(String tenantId) {
    public LeaderboardScope {
        Objects.requireNonNull(tenantId, "tenantId");
        tenantId = tenantId.trim();
        if (tenantId.isEmpty()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }
}