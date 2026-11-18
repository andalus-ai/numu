package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.PublicInvitationRateLimitRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/** PostgreSQL atomic fixed-window limiter shared across application instances. */
public final class PostgresPublicInvitationRateLimitRepository
        implements PublicInvitationRateLimitRepository {
    private static final String SQL = """
            INSERT INTO feedback_public_invitation_rate_limits (token_hash, window_start, request_count)
            VALUES (?, ?, 1)
            ON CONFLICT (token_hash, window_start)
            DO UPDATE SET request_count = feedback_public_invitation_rate_limits.request_count + 1
            WHERE feedback_public_invitation_rate_limits.request_count < ?
            RETURNING request_count
            """;

    private final DataSource dataSource;

    public PostgresPublicInvitationRateLimitRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public boolean tryConsume(String tokenHash, Instant windowStart, int limit) {
        if (tokenHash == null || !tokenHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("tokenHash must be a SHA-256 hex digest");
        Objects.requireNonNull(windowStart, "windowStart cannot be null");
        if (limit < 1 || limit > 10_000) throw new IllegalArgumentException("limit must be in 1..10000");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setString(1, tokenHash);
            statement.setTimestamp(2, Timestamp.from(windowStart));
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException failure) {
            throw new FeedbackPersistenceException("Unable to consume public invitation rate limit", failure);
        }
    }

    public int deleteExpiredBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff cannot be null");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM feedback_public_invitation_rate_limits WHERE window_start < ?")) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new FeedbackPersistenceException("Unable to clean public invitation rate limits", failure);
        }
    }
}