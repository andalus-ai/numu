package tech.kayys.numou.history.graph.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore.CanonicalRelationship;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads latest relationship projection events from PostgreSQL's immutable outbox. */
public final class JdbcHistoricalProjectionAuditStore implements HistoricalProjectionAuditStore {
    private static final String LATEST = """
        SELECT DISTINCT ON (aggregate_id)
               aggregate_id, aggregate_revision, event_key, payload
          FROM historical_relationship_projection_outbox
         WHERE tenant_id IS NOT DISTINCT FROM ?
         ORDER BY aggregate_id, aggregate_revision DESC
        """;
    private final DataSource source;
    private final ObjectMapper mapper;

    public JdbcHistoricalProjectionAuditStore(DataSource source) {
        this(source, new ObjectMapper());
    }

    JdbcHistoricalProjectionAuditStore(DataSource source, ObjectMapper mapper) {
        this.source = Objects.requireNonNull(source, "source");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<CanonicalRelationship> nextLatestPage(UUID tenantId, String afterRelationshipId, int limit) {
        validateLimit(limit);
        String sql = "SELECT * FROM (" + LATEST + ") latest WHERE (? IS NULL OR aggregate_id::text > ?) "
                + "ORDER BY aggregate_id::text LIMIT ?";
        List<CanonicalRelationship> result = new ArrayList<>();
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setString(2, afterRelationshipId);
            s.setString(3, afterRelationshipId);
            s.setInt(4, limit);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) result.add(map(r));
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw failure("read canonical historical projection audit page", e);
        }
    }

    @Override
    public Optional<CanonicalRelationship> findLatest(UUID tenantId, UUID relationshipId) {
        String sql = LATEST + " AND aggregate_id = ?"; // predicate is applied in the wrapper below
        sql = "SELECT * FROM (" + LATEST + ") latest WHERE aggregate_id = ?";
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setObject(2, relationshipId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure("read canonical historical relationship", e);
        }
    }

    private CanonicalRelationship map(ResultSet r) throws SQLException {
        UUID id = r.getObject("aggregate_id", UUID.class);
        long revision = r.getLong("aggregate_revision");
        String eventKey = r.getString("event_key");
        String payload = r.getString("payload");
        try {
            JsonNode p = mapper.readTree(payload);
            JsonNode subject = p.path("subject");
            JsonNode object = p.path("object");
            return new CanonicalRelationship(id, revision, eventKey,
                    required(p, "relationshipKind"), required(subject, "kind"), required(subject, "id"),
                    required(object, "kind"), required(object, "id"), payload);
        } catch (Exception e) {
            throw new SQLException("Invalid canonical relationship projection payload for " + id, e);
        }
    }

    private static String required(JsonNode node, String field) throws SQLException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new SQLException("Canonical projection payload missing text field: " + field);
        }
        return value.asText();
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }

    private static IllegalStateException failure(String operation, SQLException e) {
        return new IllegalStateException("Unable to " + operation, e);
    }
}
