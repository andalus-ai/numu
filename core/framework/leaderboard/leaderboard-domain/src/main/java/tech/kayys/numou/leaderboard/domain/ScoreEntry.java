package tech.kayys.numou.leaderboard.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable score-ledger fact. Corrections append new entries; existing entries never change. */
public record ScoreEntry(
        ScoreEntryId id,
        LeaderboardId leaderboardId,
        LeaderboardScope scope,
        ParticipantRef participant,
        ScoreEntryKind kind,
        ScoreValue value,
        SourceReference source,
        IdempotencyKey idempotencyKey,
        ScoreEntryId reversesEntryId,
        String reason,
        Instant effectiveAt,
        Instant recordedAt) {

    public ScoreEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leaderboardId, "leaderboardId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (value.amount().signum() == 0) throw new IllegalArgumentException("score entry value must not be zero");
        if (kind == ScoreEntryKind.REVERSAL && reversesEntryId == null) {
            throw new IllegalArgumentException("reversal must reference the entry it reverses");
        }
        if (kind != ScoreEntryKind.REVERSAL && reversesEntryId != null) {
            throw new IllegalArgumentException("only reversal entries may reference a reversed entry");
        }
        if (kind == ScoreEntryKind.ADJUSTMENT) {
            reason = requireText(reason, "reason");
        } else if (reason != null) {
            reason = reason.trim().isEmpty() ? null : reason.trim();
        }
    }

    public static ScoreEntry contribution(ScoreEntryId id, LeaderboardId leaderboardId,
            LeaderboardScope scope, ParticipantRef participant, ScoreValue value,
            SourceReference source, IdempotencyKey key, Instant effectiveAt, Instant recordedAt) {
        return new ScoreEntry(id, leaderboardId, scope, participant, ScoreEntryKind.CONTRIBUTION,
                value, source, key, null, null, effectiveAt, recordedAt);
    }

    public static ScoreEntry adjustment(ScoreEntryId id, LeaderboardId leaderboardId,
            LeaderboardScope scope, ParticipantRef participant, ScoreValue value,
            SourceReference source, IdempotencyKey key, String reason,
            Instant effectiveAt, Instant recordedAt) {
        return new ScoreEntry(id, leaderboardId, scope, participant, ScoreEntryKind.ADJUSTMENT,
                value, source, key, null, reason, effectiveAt, recordedAt);
    }

    public static ScoreEntry reversal(ScoreEntryId id, ScoreEntry original,
            SourceReference source, IdempotencyKey key, Instant effectiveAt, Instant recordedAt) {
        Objects.requireNonNull(original, "original");
        if (original.kind() == ScoreEntryKind.REVERSAL) {
            throw new IllegalArgumentException("a reversal cannot itself be reversed");
        }
        return new ScoreEntry(id, original.leaderboardId(), original.scope(), original.participant(),
                ScoreEntryKind.REVERSAL, original.value().negate(), source, key,
                original.id(), "Reversal of " + original.id().value(), effectiveAt, recordedAt);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}