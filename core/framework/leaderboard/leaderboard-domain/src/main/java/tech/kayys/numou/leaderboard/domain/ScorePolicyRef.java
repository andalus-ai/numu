package tech.kayys.numou.leaderboard.domain;

import java.util.Objects;

/** Immutable reference to a separately versioned, deterministic scoring policy. */
public record ScorePolicyRef(String policyKey, int version) {
    public ScorePolicyRef {
        Objects.requireNonNull(policyKey, "policyKey");
        policyKey = policyKey.trim();
        if (policyKey.isEmpty()) throw new IllegalArgumentException("policyKey must not be blank");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }
}