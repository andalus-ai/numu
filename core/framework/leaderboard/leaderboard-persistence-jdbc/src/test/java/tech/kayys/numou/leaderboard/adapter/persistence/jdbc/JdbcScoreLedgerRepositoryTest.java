package tech.kayys.numou.leaderboard.adapter.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.leaderboard.domain.*;

import org.postgresql.ds.PGSimpleDataSource;
import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcScoreLedgerRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcScoreLedgerRepository repository;
    private DataSource dataSource;
    private final LeaderboardId board = LeaderboardId.of(java.util.UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private final LeaderboardScope tenant = new LeaderboardScope("tenant-a");
    private final ParticipantRef participant = new ParticipantRef("driver", "driver-7");
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        resetSchema(dataSource);
        repository = new JdbcScoreLedgerRepository(dataSource);
    }

    private void resetSchema(DataSource source) throws Exception {
        String migration;
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V1__leaderboard_score_ledger.sql")) {
            if (stream == null) throw new IllegalStateException("score ledger migration missing from test classpath");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS leaderboard_score_entry CASCADE");
            statement.execute("DROP FUNCTION IF EXISTS leaderboard_score_validate_reversal() CASCADE");
            statement.execute("DROP FUNCTION IF EXISTS leaderboard_score_reject_mutation() CASCADE");
            statement.execute(migration);
        }
    }

    private ScoreEntry contribution(String key, String value, Instant recordedAt) {
        return ScoreEntry.contribution(ScoreEntryId.newId(), board, tenant, participant,
                ScoreValue.of(value), new SourceReference("mobility.trip", "trip-99"),
                new IdempotencyKey(key), NOW, recordedAt);
    }

    @Test
    void appendsPersistsAndAggregatesAcrossConnections() {
        ScoreEntry first = contribution("trip-99", "12.25", NOW);
        ScoreEntry second = contribution("trip-100", "0.75", NOW);
        assertThat(repository.append(first).status()).isEqualTo(ScoreLedger.AppendStatus.APPENDED);
        assertThat(repository.append(second).status()).isEqualTo(ScoreLedger.AppendStatus.APPENDED);
        assertThat(repository.totalFor(tenant, board, participant)).isEqualTo(ScoreValue.of("13"));
        assertThat(repository.findByIdempotencyKey(tenant, board, new IdempotencyKey("trip-99")))
                .contains(first);
    }

    @Test
    void duplicateDeliveryWithNewGeneratedIdIsIdempotent() {
        ScoreEntry first = contribution("event-1", "10", NOW);
        ScoreEntry retry = contribution("event-1", "10", NOW.plusSeconds(4));
        assertThat(repository.append(first).status()).isEqualTo(ScoreLedger.AppendStatus.APPENDED);
        assertThat(repository.append(retry).status()).isEqualTo(ScoreLedger.AppendStatus.DUPLICATE);
        assertThat(repository.totalFor(tenant, board, participant)).isEqualTo(ScoreValue.of("10"));
        ScoreEntry conflicting = contribution("event-1", "11", NOW);
        assertThatThrownBy(() -> repository.append(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different score entry");
    }

    @Test
    void rejectsReversalOfUnknownEntryAndPersistsValidReversal() {
        ScoreEntry original = contribution("event-2", "10", NOW);
        ScoreEntry reversal = ScoreEntry.reversal(ScoreEntryId.newId(), original,
                new SourceReference("moderation.case", "case-7"),
                new IdempotencyKey("reversal-1"), NOW, NOW.plusSeconds(1));
        assertThatThrownBy(() -> repository.append(reversal))
                .isInstanceOf(IllegalArgumentException.class);

        repository.append(original);
        assertThat(repository.append(reversal).status()).isEqualTo(ScoreLedger.AppendStatus.APPENDED);
        assertThat(repository.totalFor(tenant, board, participant)).isEqualTo(ScoreValue.of(0));
        ScoreEntry secondReversal = ScoreEntry.reversal(ScoreEntryId.newId(), original,
                new SourceReference("moderation.case", "case-8"),
                new IdempotencyKey("reversal-2"), NOW, NOW.plusSeconds(2));
        assertThatThrownBy(() -> repository.append(secondReversal))
                .isInstanceOf(ScoreLedgerPersistenceException.class);
    }

    @Test
    void databaseRejectsUpdatesAndDeletes() throws Exception {
        ScoreEntry entry = contribution("event-3", "5", NOW);
        repository.append(entry);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE leaderboard_score_entry SET score_value = 99 WHERE tenant_id = 'tenant-a'"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "DELETE FROM leaderboard_score_entry WHERE tenant_id = 'tenant-a'"))
                    .hasMessageContaining("append-only");
        }
    }

    @Test
    void tenantScopeIsPartOfEveryReadKey() {
        repository.append(contribution("tenant-key", "7", NOW));
        assertThat(repository.totalFor(new LeaderboardScope("tenant-b"), board, participant))
                .isEqualTo(ScoreValue.of(0));
        assertThat(repository.findByIdempotencyKey(new LeaderboardScope("tenant-b"), board,
                new IdempotencyKey("tenant-key"))).isEmpty();
    }
}
