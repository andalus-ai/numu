package tech.kayys.numou.warehouse.persistence.jdbc;

import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditExportAccessEvent;
import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditExportAccessRepository;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

/** Append-only JDBC persistence for recovery-audit export access events. */
public final class JdbcWarehouseRecoveryAuditExportAccessRepository implements WarehouseRecoveryAuditExportAccessRepository {
    private final DataSource dataSource;
    public JdbcWarehouseRecoveryAuditExportAccessRepository(DataSource dataSource) { this.dataSource=Objects.requireNonNull(dataSource,"dataSource"); }
    @Override public void append(WarehouseRecoveryAuditExportAccessEvent e) {
        Objects.requireNonNull(e,"event");
        String sql="INSERT INTO warehouse_recovery_audit_export_access (event_id,tenant_id,export_id,actor_id,action,outcome,reason_code,occurred_at) VALUES (?,?,?,?,?,?,?,?)";
        try(Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,e.eventId()); p.setString(2,e.tenantId()); p.setString(3,e.exportId()); p.setString(4,e.actorId()); p.setString(5,e.action().name()); p.setString(6,e.outcome().name()); p.setString(7,e.reasonCode()); p.setTimestamp(8,Timestamp.from(e.occurredAt())); p.executeUpdate();
        } catch(SQLException ex) { throw new IllegalStateException("Could not append recovery audit export access event",ex); }
    }
}
