package tech.kayys.numou.leaderboard.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory domain aggregate expressing append-only ledger invariants.
 * Durable concurrency/idempotency is enforced by the application persistence
 * adapter using tenant-scoped database uniqueness constraints.
 */
public final class ScoreLedger {
    public enum AppendStatus { APPENDED, DUPLICATE }

    public record AppendResult(AppendStatus status, ScoreEntry entry) {
        public AppendResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(entry, "entry");
        }
    }

    private final LeaderboardId leaderboardId;
    private final LeaderboardScope scope;
    private final String participantType;
    private final Map<ScoreEntryId, ScoreEntry> entries = new LinkedHashMap<>();
    private final Map<IdempotencyKey, ScoreEntry> byKey = new LinkedHashMap<>();
    private final Map<ScoreEntryId, ScoreEntry> reversals = new LinkedHashMap<>();

    public ScoreLedger(LeaderboardId leaderboardId, LeaderboardScope scope, String participantType) {
        this.leaderboardId = Objects.requireNonNull(leaderboardId, "leaderboardId");
        this.scope = Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(participantType, "participantType");
        this.participantType = participantType.trim();
        if (this.participantType.isEmpty()) throw new IllegalArgumentException("participantType must not be blank");
    }

    public AppendResult append(ScoreEntry candidate) {
        Objects.requireNonNull(candidate, "candidate");
        ScoreEntry existing = byKey.get(candidate.idempotencyKey());
        if (existing != null) {
            if (existing.sameIdempotentPayload(candidate)) return new AppendResult(AppendStatus.DUPLICATE, existing);
            throw new IllegalStateException("idempotency key was already used for a different score entry");
        }
        validateScopeAndParticipant(candidate);
        if (entries.containsKey(candidate.id())) {
            throw new IllegalStateException("score entry id already exists");
        }
        if (candidate.kind() == ScoreEntryKind.REVERSAL) {
            ScoreEntry original = entries.get(candidate.reversesEntryId());
            if (original == null) throw new IllegalArgumentException("reversed score entry does not exist in this ledger");
            if (original.kind() == ScoreEntryKind.REVERSAL) throw new IllegalArgumentException("cannot reverse a reversal");
            if (reversals.containsKey(original.id())) throw new IllegalStateException("score entry has already been reversed");
            if (!original.participant().equals(candidate.participant())
                    || !original.value().negate().equals(candidate.value())) {
                throw new IllegalArgumentException("reversal must negate the original entry for the same participant");
            }
        }
        entries.put(candidate.id(), candidate);
        byKey.put(candidate.idempotencyKey(), candidate);
        if (candidate.kind() == ScoreEntryKind.REVERSAL) {
            reversals.put(candidate.reversesEntryId(), candidate);
        }
        return new AppendResult(AppendStatus.APPENDED, candidate);
    }

    private void validateScopeAndParticipant(ScoreEntry entry) {
        if (!leaderboardId.equals(entry.leaderboardId())) throw new IllegalArgumentException("entry belongs to another leaderboard");
        if (!scope.equals(entry.scope())) throw new IllegalArgumentException("entry belongs to another tenant scope");
        if (!participantType.equals(entry.participant().subjectType())) {
            throw new IllegalArgumentException("participant type does not match leaderboard subject type");
        }
    }

    public ScoreValue totalFor(ParticipantRef participant) {
        Objects.requireNonNull(participant, "participant");
        if (!participantType.equals(participant.subjectType())) {
            throw new IllegalArgumentException("participant type does not match leaderboard subject type");
        }
        ScoreValue total = ScoreValue.of(0);
        for (ScoreEntry entry : entries.values()) {
            if (entry.participant().equals(participant)) total = total.plus(entry.value());
        }
        return total;
    }

    public List<ScoreEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public int size() { return entries.size(); }
    public LeaderboardId leaderboardId() { return leaderboardId; }
    public LeaderboardScope scope() { return scope; }
}