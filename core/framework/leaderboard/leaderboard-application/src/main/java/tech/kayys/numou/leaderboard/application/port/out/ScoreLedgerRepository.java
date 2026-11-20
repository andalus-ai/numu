package tech.kayys.numou.leaderboard.application.port.out;

import tech.kayys.numou.leaderboard.domain.IdempotencyKey;
import tech.kayys.numou.leaderboard.domain.LeaderboardId;
import tech.kayys.numou.leaderboard.domain.LeaderboardScope;
import tech.kayys.numou.leaderboard.domain.ParticipantRef;
import tech.kayys.numou.leaderboard.domain.ScoreEntry;
import tech.kayys.numou.leaderboard.domain.ScoreLedger;
import tech.kayys.numou.leaderboard.domain.ScoreValue;

import java.util.Optional;

/**
 * Durable append-only score store. Implementations enforce idempotency and
 * reversal uniqueness atomically across processes and database connections.
 */
public interface ScoreLedgerRepository {
    ScoreLedger.AppendResult append(ScoreEntry entry);

    Optional<ScoreEntry> findByIdempotencyKey(
            LeaderboardScope scope, LeaderboardId leaderboardId, IdempotencyKey key);

    ScoreValue totalFor(LeaderboardScope scope, LeaderboardId leaderboardId, ParticipantRef participant);
}
