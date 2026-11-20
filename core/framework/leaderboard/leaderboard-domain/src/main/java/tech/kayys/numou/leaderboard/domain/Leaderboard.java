package tech.kayys.numou.leaderboard.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate defining a tenant-scoped ranking. It owns configuration and
 * lifecycle only; source facts, participant identity and rewards remain owned
 * by their respective capabilities.
 */
public final class Leaderboard {
    private final LeaderboardId id;
    private final LeaderboardScope scope;
    private final String name;
    private final String subjectType;
    private final ScorePolicyRef scorePolicy;
    private final RankingDirection direction;
    private final TiePolicy tiePolicy;
    private final Instant createdAt;
    private LeaderboardStatus status;
    private Instant activatedAt;
    private Instant completedAt;

    private Leaderboard(LeaderboardId id, LeaderboardScope scope, String name,
                        String subjectType, ScorePolicyRef scorePolicy,
                        RankingDirection direction, TiePolicy tiePolicy,
                        Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.name = requireText(name, "name");
        this.subjectType = requireText(subjectType, "subjectType");
        this.scorePolicy = Objects.requireNonNull(scorePolicy, "scorePolicy");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.tiePolicy = Objects.requireNonNull(tiePolicy, "tiePolicy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = LeaderboardStatus.DRAFT;
    }

    public static Leaderboard create(LeaderboardId id, LeaderboardScope scope,
                                     String name, String subjectType,
                                     ScorePolicyRef scorePolicy,
                                     RankingDirection direction,
                                     TiePolicy tiePolicy, Instant createdAt) {
        return new Leaderboard(id, scope, name, subjectType, scorePolicy,
                direction, tiePolicy, createdAt);
    }

    public void activate(Instant at) {
        requireStatus(LeaderboardStatus.DRAFT, LeaderboardStatus.PAUSED);
        this.activatedAt = requireNotBeforeCreation(at);
        this.status = LeaderboardStatus.ACTIVE;
    }

    public void pause() {
        requireStatus(LeaderboardStatus.ACTIVE);
        this.status = LeaderboardStatus.PAUSED;
    }

    public void complete(Instant at) {
        requireStatus(LeaderboardStatus.ACTIVE, LeaderboardStatus.PAUSED);
        Instant completed = Objects.requireNonNull(at, "at");
        if (completed.isBefore(createdAt)) {
            throw new IllegalArgumentException("completion time cannot precede creation");
        }
        this.completedAt = completed;
        this.status = LeaderboardStatus.COMPLETED;
    }

    public void archive() {
        requireStatus(LeaderboardStatus.COMPLETED);
        this.status = LeaderboardStatus.ARCHIVED;
    }

    private Instant requireNotBeforeCreation(Instant at) {
        Instant value = Objects.requireNonNull(at, "at");
        if (value.isBefore(createdAt)) {
            throw new IllegalArgumentException("activation time cannot precede creation");
        }
        return value;
    }

    private void requireStatus(LeaderboardStatus... allowed) {
        for (LeaderboardStatus candidate : allowed) {
            if (status == candidate) return;
        }
        throw new IllegalStateException("Cannot transition leaderboard from " + status);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    public LeaderboardId id() { return id; }
    public LeaderboardScope scope() { return scope; }
    public String name() { return name; }
    public String subjectType() { return subjectType; }
    public ScorePolicyRef scorePolicy() { return scorePolicy; }
    public RankingDirection direction() { return direction; }
    public TiePolicy tiePolicy() { return tiePolicy; }
    public Instant createdAt() { return createdAt; }
    public LeaderboardStatus status() { return status; }
    public Instant activatedAt() { return activatedAt; }
    public Instant completedAt() { return completedAt; }
}