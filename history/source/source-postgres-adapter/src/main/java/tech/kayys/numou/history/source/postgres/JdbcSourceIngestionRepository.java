package tech.kayys.numou.history.source.postgres;

import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.SourceIngestionRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** PostgreSQL persistence for immutable ingestion requests and extraction provenance. */
public final class JdbcSourceIngestionRepository implements SourceIngestionRepository {
    private final DataSource dataSource;

    public JdbcSourceIngestionRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public boolean appendIngestion(UUID tenantId, SourceIngestionRequest request) {
        Objects.requireNonNull(request, "request");
        return write(tenantId, connection -> {
            String sql = "insert into historical_source_ingestion "
                    + "(tenant_id,ingestion_id,source_id,input_kind,input_document_id,input_document_version_id,"
                    + "input_sha256,input_content_type,input_uri,requested_by,requested_at) values (?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, request.id());
                statement.setObject(3, request.sourceId().value());
                statement.setString(4, request.inputKind().name());
                statement.setObject(5, request.inputDocumentId());
                statement.setObject(6, request.inputDocumentVersionId());
                statement.setString(7, request.inputSha256());
                statement.setString(8, request.inputContentType());
                statement.setString(9, request.inputUri());
                statement.setString(10, request.requestedBy());
                statement.setTimestamp(11, Timestamp.from(request.requestedAt()));
                statement.executeUpdate();
                return true;
            }
        });
    }

    @Override
    public Optional<SourceIngestionRequest> findIngestion(UUID tenantId, UUID ingestionId) {
        Objects.requireNonNull(ingestionId, "ingestionId");
        return read(tenantId, connection -> {
            String sql = "select ingestion_id,source_id,input_kind,input_document_id,input_document_version_id,"
                    + "input_sha256,input_content_type,input_uri,requested_by,requested_at "
                    + "from historical_source_ingestion where tenant_id=? and ingestion_id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, ingestionId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapIngestion(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<SourceIngestionRequest> listIngestions(UUID tenantId, UUID sourceId, int limit) {
        checkLimit(limit);
        Objects.requireNonNull(sourceId, "sourceId");
        return read(tenantId, connection -> {
            String sql = "select ingestion_id,source_id,input_kind,input_document_id,input_document_version_id,"
                    + "input_sha256,input_content_type,input_uri,requested_by,requested_at "
                    + "from historical_source_ingestion where tenant_id=? and source_id=? "
                    + "order by requested_at,ingestion_id limit ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, sourceId);
                statement.setInt(3, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<SourceIngestionRequest> requests = new ArrayList<>();
                    while (result.next()) requests.add(mapIngestion(result));
                    return List.copyOf(requests);
                }
            }
        });
    }

    @Override
    public boolean appendExtraction(UUID tenantId, SourceExtractionArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return write(tenantId, connection -> {
            String sql = "insert into historical_source_extraction "
                    + "(tenant_id,extraction_id,ingestion_id,extraction_kind,extractor_id,extractor_version,"
                    + "output_document_id,output_document_version_id,input_sha256,output_sha256,language_tag,"
                    + "locator,worker_ref,extracted_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, artifact.id());
                statement.setObject(3, artifact.ingestionId());
                statement.setString(4, artifact.kind().name());
                statement.setString(5, artifact.extractorId());
                statement.setString(6, artifact.extractorVersion());
                statement.setObject(7, artifact.outputDocumentId());
                statement.setObject(8, artifact.outputDocumentVersionId());
                statement.setString(9, artifact.inputSha256());
                statement.setString(10, artifact.outputSha256());
                statement.setString(11, artifact.languageTag());
                statement.setString(12, artifact.locator());
                statement.setString(13, artifact.workerRef());
                statement.setTimestamp(14, Timestamp.from(artifact.extractedAt()));
                statement.executeUpdate();
                return true;
            }
        });
    }

    @Override
    public List<SourceExtractionArtifact> listExtractions(UUID tenantId, UUID ingestionId, int limit) {
        checkLimit(limit);
        Objects.requireNonNull(ingestionId, "ingestionId");
        return read(tenantId, connection -> {
            String sql = "select extraction_id,ingestion_id,extraction_kind,extractor_id,extractor_version,"
                    + "output_document_id,output_document_version_id,input_sha256,output_sha256,language_tag,"
                    + "locator,worker_ref,extracted_at from historical_source_extraction "
                    + "where tenant_id=? and ingestion_id=? order by extracted_at,extraction_id limit ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, ingestionId);
                statement.setInt(3, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<SourceExtractionArtifact> artifacts = new ArrayList<>();
                    while (result.next()) artifacts.add(mapExtraction(result));
                    return List.copyOf(artifacts);
                }
            }
        });
    }

    private static SourceIngestionRequest mapIngestion(ResultSet result) throws SQLException {
        return new SourceIngestionRequest(
                result.getObject(1, UUID.class),
                new HistoricalSourceId(result.getObject(2, UUID.class)),
                SourceIngestionInputKind.valueOf(result.getString(3)),
                result.getObject(4, UUID.class),
                result.getObject(5, UUID.class),
                result.getString(6),
                result.getString(7),
                result.getString(8),
                result.getString(9),
                result.getTimestamp(10).toInstant());
    }

    private static SourceExtractionArtifact mapExtraction(ResultSet result) throws SQLException {
        return new SourceExtractionArtifact(
                result.getObject(1, UUID.class),
                result.getObject(2, UUID.class),
                SourceExtractionKind.valueOf(result.getString(3)),
                result.getString(4),
                result.getString(5),
                result.getObject(6, UUID.class),
                result.getObject(7, UUID.class),
                result.getString(8),
                result.getString(9),
                result.getString(10),
                result.getString(11),
                result.getString(12),
                result.getTimestamp(13).toInstant());
    }

    private static void checkLimit(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T read(UUID tenantId, SqlWork<T> work) {
        return execute(tenantId, work, true);
    }

    private boolean write(UUID tenantId, SqlWork<Boolean> work) {
        try {
            return execute(tenantId, work, false);
        } catch (IllegalStateException failure) {
            if (failure.getCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) return false;
            throw failure;
        }
    }

    private <T> T execute(UUID tenantId, SqlWork<T> work, boolean readOnly) {
        Objects.requireNonNull(tenantId, "tenantId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(readOnly);
            try (PreparedStatement tenantSetting = connection.prepareStatement(
                    "select set_config('numou.tenant_id', ?, true)")) {
                tenantSetting.setString(1, tenantId.toString());
                tenantSetting.execute();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Source ingestion persistence failed", failure);
        }
    }
}
