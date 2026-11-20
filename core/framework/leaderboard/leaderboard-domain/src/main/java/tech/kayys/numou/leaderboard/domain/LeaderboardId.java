package tech.kayys.numou.leaderboard.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a leaderboard aggregate. */
public record LeaderboardId(UUID value) {
    public LeaderboardId {
        Objects.requireNonNull(value, "value");
    }

    public static LeaderboardId newId() {
        return new LeaderboardId(UUID.randomUUID());
    }

    public static LeaderboardId of(UUID value) {
        return new LeaderboardId(value);
    }
}