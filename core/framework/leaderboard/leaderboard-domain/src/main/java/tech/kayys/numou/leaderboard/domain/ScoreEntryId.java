package tech.kayys.numou.leaderboard.domain;

import java.util.Objects;
import java.util.UUID;

public record ScoreEntryId(UUID value) {
    public ScoreEntryId { Objects.requireNonNull(value, "value"); }
    public static ScoreEntryId newId() { return new ScoreEntryId(UUID.randomUUID()); }
    public static ScoreEntryId of(UUID value) { return new ScoreEntryId(value); }
}