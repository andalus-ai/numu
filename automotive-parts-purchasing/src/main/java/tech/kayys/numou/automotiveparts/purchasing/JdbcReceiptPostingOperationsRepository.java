package tech.kayys.numou.automotiveparts.purchasing;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Tenant-scoped read model for operator inspection of receipt posting dead letters. */
public final class JdbcReceiptPostingOperationsRepository {
    private final DataSource dataSource;

    public JdbcReceiptPostingOperationsRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    public ReceiptPostingOperationsSummary summary(String tenantId) {
        require(tenantId, "tenantId");
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT
                       COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_count,
                       COUNT(*) FILTER (WHERE status = 'POSTED') AS posted_count,
                       COUNT(*) FILTER (WHERE status = 'DEAD_LETTER') AS dead_letter_count,
                       MIN(dead_lettered_at) FILTER (WHERE status = 'DEAD_LETTER') AS oldest_dead_letter_at
                     FROM purchasing_receipt_inventory_posting
                     WHERE tenant_id = ?
                     """)) {
            statement.setString(1, tenantId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new SQLException("Receipt posting summary returned no row");
                Timestamp oldest = rs.getTimestamp("oldest_dead_letter_at");
                return new ReceiptPostingOperationsSummary(
                        rs.getLong("pending_count"),
                        rs.getLong("posted_count"),
                        rs.getLong("dead_letter_count"),
                        oldest == null ? null : oldest.toInstant());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query receipt posting operations summary", e);
        }
    }

    public DeadLetterReceiptPostingPage findDeadLetters(String tenantId, String cursor, int limit) {
        require(tenantId, "tenantId");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        Cursor after = cursor == null || cursor.isBlank() ? null : decode(cursor);
        String sql = """
                SELECT receipt_id, attempts, max_attempts, last_failure, terminal_failure_reason,
                       dead_lettered_at, created_at, updated_at
                FROM purchasing_receipt_inventory_posting
                WHERE tenant_id = ? AND status = 'DEAD_LETTER'
                """ + (after == null ? "" : " AND (dead_lettered_at, receipt_id) > (?, ?) ") + """
                ORDER BY dead_lettered_at ASC, receipt_id ASC
                LIMIT ?
                """;
        try (var connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            int parameter = 2;
            if (after != null) {
                statement.setTimestamp(parameter++, Timestamp.from(after.deadLetteredAt()));
                statement.setString(parameter++, after.receiptId());
            }
            statement.setInt(parameter, limit + 1);
            List<DeadLetterReceiptPosting> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) rows.add(read(rs));
            }
            boolean hasMore = rows.size() > limit;
            if (hasMore) rows.remove(rows.size() - 1);
            String next = hasMore && !rows.isEmpty()
                    ? encode(rows.get(rows.size() - 1).deadLetteredAt(), rows.get(rows.size() - 1).receiptId())
                    : null;
            return new DeadLetterReceiptPostingPage(rows, next);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query dead-letter receipt postings", e);
        }
    }

    public Optional<DeadLetterReceiptPosting> findDeadLetter(String tenantId, String receiptId) {
        require(tenantId, "tenantId"); require(receiptId, "receiptId");
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT receipt_id, attempts, max_attempts, last_failure, terminal_failure_reason,
                            dead_lettered_at, created_at, updated_at
                     FROM purchasing_receipt_inventory_posting
                     WHERE tenant_id = ? AND receipt_id = ? AND status = 'DEAD_LETTER'
                     """)) {
            statement.setString(1, tenantId); statement.setString(2, receiptId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query dead-letter receipt posting", e);
        }
    }

    public Optional<String> findFingerprint(String tenantId, String receiptId) {
        require(tenantId, "tenantId"); require(receiptId, "receiptId");
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT payload_fingerprint FROM purchasing_receipt_inventory_posting
                     WHERE tenant_id = ? AND receipt_id = ? AND status = 'DEAD_LETTER'
                     """)) {
            statement.setString(1, tenantId); statement.setString(2, receiptId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not resolve dead-letter receipt identity", e);
        }
    }

    private static DeadLetterReceiptPosting read(ResultSet rs) throws SQLException {
        Timestamp dead = rs.getTimestamp("dead_lettered_at");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new DeadLetterReceiptPosting(rs.getString("receipt_id"), rs.getInt("attempts"),
                rs.getInt("max_attempts"), rs.getString("last_failure"),
                rs.getString("terminal_failure_reason"), dead == null ? null : dead.toInstant(),
                created == null ? null : created.toInstant(), updated == null ? null : updated.toInstant());
    }

    private static String encode(Instant instant, String receiptId) {
        String raw = instant.toString() + "\n" + receiptId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.indexOf('\n');
            if (separator < 1 || separator == raw.length() - 1) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(raw.substring(0, separator)), raw.substring(separator + 1));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid dead-letter page cursor", invalid);
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
    private record Cursor(Instant deadLetteredAt, String receiptId) {}
}
