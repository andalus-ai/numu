package tech.kayys.numou.automotiveparts.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.automotiveparts.domain.AutomotivePartProfile;
import tech.kayys.numou.automotiveparts.domain.PartFitment;
import tech.kayys.numou.automotiveparts.domain.PartReference;
import tech.kayys.numou.automotiveparts.domain.PartReferenceType;
import tech.kayys.numou.automotiveparts.domain.PartSubstitution;
import tech.kayys.numou.automotiveparts.domain.VehicleFitment;
import tech.kayys.numou.automotiveparts.domain.VehicleSpecification;
import tech.kayys.numou.automotiveparts.spi.AutomotivePartRepository;
import tech.kayys.numou.product.domain.product.ProductId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL persistence adapter. Tenant identifiers are always part of query
 * predicates and composite foreign keys; database uniqueness arbitrates races.
 */
@ApplicationScoped
public class JdbcAutomotivePartRepository implements AutomotivePartRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcAutomotivePartRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<AutomotivePartProfile> findProfile(String tenantId, ProductId productId) {
        requireTenant(tenantId);
        if (productId == null) throw new IllegalArgumentException("productId is required");
        try (Connection connection = dataSource.getConnection()) {
            return loadProfile(connection, tenantId.trim(), productId);
        } catch (SQLException e) {
            throw persistenceFailure("find automotive part profile", e);
        }
    }

    @Override
    public Optional<AutomotivePartProfile> findByReference(String tenantId, PartReference reference) {
        requireTenant(tenantId);
        if (reference == null) throw new IllegalArgumentException("reference is required");
        String sql = """
                SELECT product_id FROM automotive_part_reference
                 WHERE tenant_id = ? AND reference_type = ? AND normalized_value = ?
                   AND COALESCE(issuer, '') = COALESCE(?, '')
                   AND COALESCE(market, '') = COALESCE(?, '')
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId.trim());
            statement.setString(2, reference.type().name());
            statement.setString(3, reference.value());
            statement.setString(4, reference.issuer());
            statement.setString(5, reference.market());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                ProductId id = ProductId.of(rows.getObject("product_id", UUID.class));
                if (rows.next()) throw new IllegalStateException("Reference namespace is not unique");
                return loadProfile(connection, tenantId.trim(), id);
            }
        } catch (SQLException e) {
            throw persistenceFailure("find automotive part by reference", e);
        }
    }

    @Override
    public void saveProfile(AutomotivePartProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile is required");
        String upsert = """
                INSERT INTO automotive_part_profile (tenant_id, product_id, part_kind, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, product_id) DO UPDATE
                   SET part_kind = EXCLUDED.part_kind,
                       description = EXCLUDED.description,
                       updated_at = now()
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                    statement.setString(1, profile.tenantId());
                    statement.setObject(2, profile.productId().value());
                    statement.setString(3, profile.kind().name());
                    statement.setString(4, profile.description());
                    statement.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM automotive_part_reference WHERE tenant_id = ? AND product_id = ?")) {
                    delete.setString(1, profile.tenantId());
                    delete.setObject(2, profile.productId().value());
                    delete.executeUpdate();
                }
                String insertSql = """
                        INSERT INTO automotive_part_reference
                            (tenant_id, product_id, reference_type, reference_value, issuer, market, normalized_value)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    for (PartReference reference : profile.references()) {
                        insert.setString(1, profile.tenantId());
                        insert.setObject(2, profile.productId().value());
                        insert.setString(3, reference.type().name());
                        insert.setString(4, reference.value());
                        insert.setString(5, reference.issuer());
                        insert.setString(6, reference.market());
                        insert.setString(7, reference.value());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (Exception failure) {
                rollback(connection, failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to persist automotive part profile", failure);
            }
        } catch (SQLException e) {
            throw persistenceFailure("save automotive part profile", e);
        }
    }


    @Override
    public void saveSubstitution(PartSubstitution substitution) {
        if (substitution == null) throw new IllegalArgumentException("substitution is required");
        String sql = """
                INSERT INTO automotive_part_substitution
                    (tenant_id, source_product_id, substitute_product_id, substitution_kind,
                     requires_technician_approval, rationale, effective_from, effective_through)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, substitution.tenantId());
            statement.setObject(2, substitution.sourceProductId().value());
            statement.setObject(3, substitution.substituteProductId().value());
            statement.setString(4, substitution.kind().name());
            statement.setBoolean(5, substitution.requiresTechnicianApproval());
            statement.setString(6, substitution.rationale());
            statement.setObject(7, substitution.effectiveFrom());
            statement.setObject(8, substitution.effectiveThrough());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw persistenceFailure("save automotive part substitution", e);
        }
    }

    @Override
    public List<PartSubstitution> findSubstitutions(String tenantId, ProductId sourceProductId, LocalDate asOf) {
        requireTenant(tenantId);
        if (sourceProductId == null || asOf == null) {
            throw new IllegalArgumentException("sourceProductId and asOf are required");
        }
        String sql = """
                SELECT substitute_product_id, substitution_kind, requires_technician_approval,
                       rationale, effective_from, effective_through
                  FROM automotive_part_substitution
                 WHERE tenant_id = ? AND source_product_id = ?
                   AND effective_from <= ? AND (effective_through IS NULL OR effective_through >= ?)
                 ORDER BY substitution_kind, substitute_product_id
                """;
        List<PartSubstitution> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId.trim());
            statement.setObject(2, sourceProductId.value());
            statement.setObject(3, asOf);
            statement.setObject(4, asOf);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.add(new PartSubstitution(
                            tenantId.trim(), sourceProductId,
                            ProductId.of(rows.getObject("substitute_product_id", UUID.class)),
                            PartSubstitution.SubstitutionKind.valueOf(rows.getString("substitution_kind")),
                            rows.getBoolean("requires_technician_approval"), rows.getString("rationale"),
                            rows.getObject("effective_from", LocalDate.class),
                            rows.getObject("effective_through", LocalDate.class)));
                }
            }
            return List.copyOf(results);
        } catch (SQLException e) {
            throw persistenceFailure("find automotive part substitutions", e);
        }
    }

    @Override
    public List<PartFitment> findFitmentCandidates(String tenantId, VehicleSpecification vehicle, LocalDate asOf) {
        requireTenant(tenantId);
        if (vehicle == null || asOf == null) throw new IllegalArgumentException("vehicle and asOf are required");
        String sql = """
                SELECT product_id, tenant_id, market, make, model, generation,
                       from_model_year, through_model_year, engine_code, trim_name,
                       evidence_source, evidence_reference, effective_from, effective_through
                  FROM automotive_vehicle_fitment
                 WHERE tenant_id = ? AND make = ? AND model = ?
                   AND from_model_year <= ? AND through_model_year >= ?
                   AND (market IS NULL OR market = ?)
                   AND effective_from <= ? AND (effective_through IS NULL OR effective_through >= ?)
                 ORDER BY product_id, effective_from, evidence_source, evidence_reference
                """;
        List<PartFitment> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId.trim());
            statement.setString(2, vehicle.make());
            statement.setString(3, vehicle.model());
            statement.setInt(4, vehicle.modelYear());
            statement.setInt(5, vehicle.modelYear());
            statement.setString(6, vehicle.market());
            statement.setObject(7, asOf);
            statement.setObject(8, asOf);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    VehicleFitment fitment = new VehicleFitment(
                            rows.getString("tenant_id"), rows.getString("market"),
                            rows.getString("make"), rows.getString("model"), rows.getString("generation"),
                            rows.getInt("from_model_year"), rows.getInt("through_model_year"),
                            rows.getString("engine_code"), rows.getString("trim_name"),
                            rows.getString("evidence_source"), rows.getString("evidence_reference"),
                            rows.getObject("effective_from", LocalDate.class),
                            rows.getObject("effective_through", LocalDate.class));
                    results.add(new PartFitment(rows.getString("tenant_id"),
                            ProductId.of(rows.getObject("product_id", UUID.class)), fitment));
                }
            }
            return List.copyOf(results);
        } catch (SQLException e) {
            throw persistenceFailure("search automotive fitment candidates", e);
        }
    }

    @Override
    public void saveFitment(PartFitment partFitment) {
        if (partFitment == null) throw new IllegalArgumentException("fitment is required");
        VehicleFitment f = partFitment.fitment();
        String sql = """
                INSERT INTO automotive_vehicle_fitment
                    (fitment_id, tenant_id, product_id, market, make, model, generation,
                     from_model_year, through_model_year, engine_code, trim_name,
                     evidence_source, evidence_reference, effective_from, effective_through)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, partFitment.tenantId());
            statement.setObject(3, partFitment.productId().value());
            statement.setString(4, f.market());
            statement.setString(5, f.make());
            statement.setString(6, f.model());
            statement.setString(7, f.generation());
            statement.setInt(8, f.fromModelYear());
            statement.setInt(9, f.throughModelYear());
            statement.setString(10, f.engineCode());
            statement.setString(11, f.trim());
            statement.setString(12, f.evidenceSource());
            statement.setString(13, f.evidenceReference());
            statement.setObject(14, f.effectiveFrom());
            statement.setObject(15, f.effectiveThrough());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw persistenceFailure("save automotive fitment", e);
        }
    }

    private Optional<AutomotivePartProfile> loadProfile(Connection connection, String tenantId, ProductId productId)
            throws SQLException {
        String sql = "SELECT part_kind, description FROM automotive_part_profile WHERE tenant_id = ? AND product_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setObject(2, productId.value());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                AutomotivePartProfile.PartKind kind = AutomotivePartProfile.PartKind.valueOf(rows.getString("part_kind"));
                String description = rows.getString("description");
                List<PartReference> references = new ArrayList<>();
                try (PreparedStatement refs = connection.prepareStatement("""
                        SELECT reference_type, reference_value, issuer, market
                          FROM automotive_part_reference
                         WHERE tenant_id = ? AND product_id = ?
                         ORDER BY reference_type, normalized_value
                        """)) {
                    refs.setString(1, tenantId);
                    refs.setObject(2, productId.value());
                    try (ResultSet referenceRows = refs.executeQuery()) {
                        while (referenceRows.next()) {
                            references.add(new PartReference(
                                    PartReferenceType.valueOf(referenceRows.getString("reference_type")),
                                    referenceRows.getString("reference_value"),
                                    referenceRows.getString("issuer"), referenceRows.getString("market")));
                        }
                    }
                }
                return Optional.of(new AutomotivePartProfile(tenantId, productId, kind, description, references));
            }
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
