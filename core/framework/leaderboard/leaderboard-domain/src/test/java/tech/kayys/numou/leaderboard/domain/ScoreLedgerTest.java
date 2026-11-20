package tech.kayys.numou.leaderboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ScoreLedgerTest {
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");
    private final LeaderboardId leaderboardId = LeaderboardId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private final LeaderboardScope scope = new LeaderboardScope("tenant-a");
    private final ParticipantRef driver = new ParticipantRef("driver", "driver-7");
    private final ScoreLedger ledger = new ScoreLedger(leaderboardId, scope, "driver");

    private ScoreEntry contribution(String key, String points) {
        return ScoreEntry.contribution(ScoreEntryId.newId(), leaderboardId, scope, driver,
                ScoreValue.of(points), new SourceReference("mobility.trip", "trip-99"),
                new IdempotencyKey(key), NOW, NOW);
    }

    @Test
    void appendsContributionAndAggregatesExactDecimalValue() {
        ledger.append(contribution("trip-99", "12.25"));
        ledger.append(contribution("trip-100", "0.75"));
        assertThat(ledger.totalFor(driver)).isEqualTo(ScoreValue.of("13.00"));
        assertThat(ledger.size()).isEqualTo(2);
    }

    @Test
    void repeatedIdenticalDeliveryIsIdempotent() {
        ScoreEntry entry = contribution("event-1", "10");
        assertThat(ledger.append(entry).status()).isEqualTo(ScoreLedger.AppendStatus.APPENDED);
        assertThat(ledger.append(entry).status()).isEqualTo(ScoreLedger.AppendStatus.DUPLICATE);
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void treatsRedeliveryWithNewEntryIdAndRecordedAtAsDuplicate() {
        ScoreEntry first = contribution("retry-event", "10");
        ScoreEntry retry = ScoreEntry.contribution(ScoreEntryId.newId(), leaderboardId, scope,
                driver, ScoreValue.of("10"), new SourceReference("mobility.trip", "trip-99"),
                new IdempotencyKey("retry-event"), NOW, NOW.plusSeconds(30));
        assertThat(ledger.append(first).status()).isEqualTo(ScoreLedger.AppendStatus.APPENDED);
        assertThat(ledger.append(retry).status()).isEqualTo(ScoreLedger.AppendStatus.DUPLICATE);
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentPayload() {
        ledger.append(contribution("event-1", "10"));
        assertThatThrownBy(() -> ledger.append(contribution("event-1", "11")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void rejectsCrossTenantAndCrossLeaderboardEntries() {
        ScoreEntry wrongTenant = ScoreEntry.contribution(ScoreEntryId.newId(), leaderboardId,
                new LeaderboardScope("tenant-b"), driver, ScoreValue.of(2),
                new SourceReference("sales.order", "order-1"), new IdempotencyKey("tenant-b-event"), NOW, NOW);
        assertThatThrownBy(() -> ledger.append(wrongTenant)).isInstanceOf(IllegalArgumentException.class);
        ScoreEntry wrongBoard = ScoreEntry.contribution(ScoreEntryId.newId(),
                LeaderboardId.newId(), scope, driver, ScoreValue.of(2),
                new SourceReference("sales.order", "order-2"), new IdempotencyKey("other-board-event"), NOW, NOW);
        assertThatThrownBy(() -> ledger.append(wrongBoard)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ledger.size()).isZero();
    }

    @Test
    void rejectsParticipantTypeMismatch() {
        ScoreEntry wrongType = ScoreEntry.contribution(ScoreEntryId.newId(), leaderboardId, scope,
                new ParticipantRef("employee", "employee-1"), ScoreValue.of(2),
                new SourceReference("workforce.shift", "shift-1"), new IdempotencyKey("wrong-type"), NOW, NOW);
        assertThatThrownBy(() -> ledger.append(wrongType)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendsAuditedAdjustmentAndReversalWithoutMutatingHistory() {
        ScoreEntry original = contribution("event-1", "10.5");
        ledger.append(original);
        ScoreEntry adjustment = ScoreEntry.adjustment(ScoreEntryId.newId(), leaderboardId, scope,
                driver, ScoreValue.of("-1.5"), new SourceReference("moderation.case", "case-3"),
                new IdempotencyKey("adjustment-1"), "Validated duplicate activity", NOW, NOW);
        ledger.append(adjustment);
        ScoreEntry reversal = ScoreEntry.reversal(ScoreEntryId.newId(), original,
                new SourceReference("moderation.case", "case-4"), new IdempotencyKey("reversal-1"), NOW, NOW);
        ledger.append(reversal);
        assertThat(ledger.totalFor(driver)).isEqualTo(ScoreValue.of("-1.5"));
        assertThat(ledger.entries()).containsExactly(original, adjustment, reversal);
        assertThat(original.value()).isEqualTo(ScoreValue.of("10.5"));
    }

    @Test
    void preventsDoubleReversalAndReversalOfUnknownEntry() {
        ScoreEntry original = contribution("event-1", "10");
        ScoreEntry reversal = ScoreEntry.reversal(ScoreEntryId.newId(), original,
                new SourceReference("moderation.case", "case-1"), new IdempotencyKey("reversal-1"), NOW, NOW);
        assertThatThrownBy(() -> ledger.append(reversal)).isInstanceOf(IllegalArgumentException.class);
        ledger.append(original);
        ledger.append(reversal);
        ScoreEntry second = ScoreEntry.reversal(ScoreEntryId.newId(), original,
                new SourceReference("moderation.case", "case-2"), new IdempotencyKey("reversal-2"), NOW, NOW);
        assertThatThrownBy(() -> ledger.append(second)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsExcessiveScaleAndZeroScore() {
        assertThatThrownBy(() -> ScoreValue.of("1.0000001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoreEntry.contribution(ScoreEntryId.newId(), leaderboardId, scope,
                driver, ScoreValue.of(0), new SourceReference("event", "zero"),
                new IdempotencyKey("zero"), NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesReadOnlyEntryHistory() {
        ledger.append(contribution("event-1", "1"));
        assertThatThrownBy(() -> ledger.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}