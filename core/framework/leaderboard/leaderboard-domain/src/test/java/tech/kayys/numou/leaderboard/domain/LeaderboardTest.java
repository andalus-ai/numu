package tech.kayys.numou.leaderboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class LeaderboardTest {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private Leaderboard draft() {
        return Leaderboard.create(
                LeaderboardId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new LeaderboardScope("tenant-1"), "Top drivers", "driver",
                new ScorePolicyRef("validated-trip-points", 1),
                RankingDirection.DESCENDING, TiePolicy.COMPETITION, CREATED);
    }

    @Test
    void createsTenantScopedDraftWithExplicitRankingPolicy() {
        Leaderboard leaderboard = draft();
        assertThat(leaderboard.status()).isEqualTo(LeaderboardStatus.DRAFT);
        assertThat(leaderboard.scope().tenantId()).isEqualTo("tenant-1");
        assertThat(leaderboard.tiePolicy()).isEqualTo(TiePolicy.COMPETITION);
        assertThat(leaderboard.direction()).isEqualTo(RankingDirection.DESCENDING);
    }

    @Test
    void supportsValidLifecycleTransitions() {
        Leaderboard leaderboard = draft();
        Instant activated = CREATED.plusSeconds(60);
        Instant completed = activated.plusSeconds(3600);
        leaderboard.activate(activated);
        assertThat(leaderboard.status()).isEqualTo(LeaderboardStatus.ACTIVE);
        leaderboard.pause();
        assertThat(leaderboard.status()).isEqualTo(LeaderboardStatus.PAUSED);
        leaderboard.activate(activated.plusSeconds(120));
        leaderboard.complete(completed);
        leaderboard.archive();
        assertThat(leaderboard.status()).isEqualTo(LeaderboardStatus.ARCHIVED);
        assertThat(leaderboard.completedAt()).isEqualTo(completed);
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        assertThatThrownBy(() -> draft().pause()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> draft().complete(CREATED.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preservesFirstActivationAcrossPauseAndResume() {
        Leaderboard leaderboard = draft();
        Instant firstActivation = CREATED.plusSeconds(60);
        leaderboard.activate(firstActivation);
        leaderboard.pause();
        leaderboard.activate(firstActivation.plusSeconds(300));
        assertThat(leaderboard.activatedAt()).isEqualTo(firstActivation);
    }

    @Test
    void rejectsCompletionBeforeActivation() {
        Leaderboard leaderboard = draft();
        Instant activated = CREATED.plusSeconds(60);
        leaderboard.activate(activated);
        leaderboard.pause();
        assertThatThrownBy(() -> leaderboard.complete(activated.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsActivationBeforeCreation() {
        assertThatThrownBy(() -> draft().activate(CREATED.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresExplicitTenantScopeAndPolicyVersion() {
        assertThatThrownBy(() -> new LeaderboardScope(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScorePolicyRef("points", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void participantReferenceRemainsOpaqueAndValidated() {
        ParticipantRef ref = new ParticipantRef("driver", "driver-42");
        assertThat(ref.subjectType()).isEqualTo("driver");
        assertThat(ref.subjectId()).isEqualTo("driver-42");
        assertThatThrownBy(() -> new ParticipantRef("driver", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}