package tech.kayys.numou.warehouse.persistence.jdbc;

import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditExportJob;
import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditExportJobRepository;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL persistence with tenant-scoped reads, optimistic updates, and atomic lease claims. */
public final class JdbcWarehouseRecoveryAuditExportJobRepository implements WarehouseRecoveryAuditExportJobRepository {
    private final DataSource dataSource;
    public JdbcWarehouseRecoveryAuditExportJobRepository(DataSource dataSource) { this.dataSource=Objects.requireNonNull(dataSource,"dataSource"); }
    @Override public void insert(WarehouseRecoveryAuditExportJob j) {
        Objects.requireNonNull(j,"job");
        String sql="INSERT INTO warehouse_recovery_audit_export_job (export_id,tenant_id,requested_by,status,continuation_cursor,exported_event_count,exported_byte_count,artifact_location,failure_code,created_at,updated_at,lease_until,lease_owner,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,j.exportId()); p.setString(2,j.tenantId()); p.setString(3,j.requestedBy()); p.setString(4,j.status().name()); p.setString(5,j.continuationCursor()); p.setLong(6,j.exportedEventCount()); p.setLong(7,j.exportedByteCount()); p.setString(8,j.artifactLocation()); p.setString(9,j.failureCode()); p.setTimestamp(10,Timestamp.from(j.createdAt())); p.setTimestamp(11,Timestamp.from(j.updatedAt())); timestamp(p,12,j.leaseUntil()); p.setString(13,j.leaseOwner()); p.setLong(14,j.version()); p.executeUpdate();
        } catch(SQLException e) { throw new IllegalStateException("Could not insert recovery audit export job",e); }
    }
    @Override public Optional<WarehouseRecoveryAuditExportJob> find(String tenantId,String exportId) {
        require(tenantId,"tenantId"); require(exportId,"exportId");
        try(Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement("SELECT * FROM warehouse_recovery_audit_export_job WHERE tenant_id=? AND export_id=?")) {
            p.setString(1,tenantId.trim()); p.setString(2,exportId.trim()); try(ResultSet r=p.executeQuery()) { return r.next()?Optional.of(map(r)):Optional.empty(); }
        } catch(SQLException e) { throw new IllegalStateException("Could not read recovery audit export job",e); }
    }
    @Override public boolean update(WarehouseRecoveryAuditExportJob j,long expectedVersion) {
        Objects.requireNonNull(j,"job"); if(expectedVersion<0||j.version()!=expectedVersion+1) throw new IllegalArgumentException("job version must be expectedVersion + 1");
        String sql="UPDATE warehouse_recovery_audit_export_job SET status=?,continuation_cursor=?,exported_event_count=?,exported_byte_count=?,artifact_location=?,failure_code=?,updated_at=?,lease_until=?,lease_owner=?,version=? WHERE tenant_id=? AND export_id=? AND version=?";
        try(Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,j.status().name()); p.setString(2,j.continuationCursor()); p.setLong(3,j.exportedEventCount()); p.setLong(4,j.exportedByteCount()); p.setString(5,j.artifactLocation()); p.setString(6,j.failureCode()); p.setTimestamp(7,Timestamp.from(j.updatedAt())); timestamp(p,8,j.leaseUntil()); p.setString(9,j.leaseOwner()); p.setLong(10,j.version()); p.setString(11,j.tenantId()); p.setString(12,j.exportId()); p.setLong(13,expectedVersion); return p.executeUpdate()==1;
        } catch(SQLException e) { throw new IllegalStateException("Could not checkpoint recovery audit export job",e); }
    }
    @Override public Optional<WarehouseRecoveryAuditExportJob> claimNext(String workerId,Instant now,Instant leaseUntil) {
        require(workerId,"workerId"); Objects.requireNonNull(now,"now"); Objects.requireNonNull(leaseUntil,"leaseUntil"); if(!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");
        String select="SELECT * FROM warehouse_recovery_audit_export_job WHERE status='REQUESTED' OR (status='RUNNING' AND lease_until<=?) ORDER BY created_at,export_id LIMIT 1 FOR UPDATE SKIP LOCKED";
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement(select)) {
                p.setTimestamp(1,Timestamp.from(now));
                try(ResultSet r=p.executeQuery()) {
                    if(!r.next()) { c.commit(); return Optional.empty(); }
                    WarehouseRecoveryAuditExportJob old=map(r);
                    try(PreparedStatement u=c.prepareStatement("UPDATE warehouse_recovery_audit_export_job SET status='RUNNING',lease_owner=?,lease_until=?,updated_at=?,version=version+1 WHERE tenant_id=? AND export_id=? AND version=?")) {
                        u.setString(1,workerId.trim()); u.setTimestamp(2,Timestamp.from(leaseUntil)); u.setTimestamp(3,Timestamp.from(now)); u.setString(4,old.tenantId()); u.setString(5,old.exportId()); u.setLong(6,old.version()); if(u.executeUpdate()!=1) throw new SQLException("Export job claim lost optimistic lock");
                    }
                    c.commit(); return Optional.of(new WarehouseRecoveryAuditExportJob(old.exportId(),old.tenantId(),old.requestedBy(),WarehouseRecoveryAuditExportJob.Status.RUNNING,old.continuationCursor(),old.exportedEventCount(),old.exportedByteCount(),old.artifactLocation(),null,old.createdAt(),now,leaseUntil,workerId.trim(),old.version()+1));
                }
            } catch(Exception e) { c.rollback(); throw e; }
        } catch(SQLException e) { throw new IllegalStateException("Could not claim recovery audit export job",e); }
    }
    private static WarehouseRecoveryAuditExportJob map(ResultSet r)throws SQLException {
        Timestamp lease=r.getTimestamp("lease_until");
        return new WarehouseRecoveryAuditExportJob(r.getString("export_id"),r.getString("tenant_id"),r.getString("requested_by"),WarehouseRecoveryAuditExportJob.Status.valueOf(r.getString("status")),r.getString("continuation_cursor"),r.getLong("exported_event_count"),r.getLong("exported_byte_count"),r.getString("artifact_location"),r.getString("failure_code"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),lease==null?null:lease.toInstant(),r.getString("lease_owner"),r.getLong("version"));
    }
    private static void timestamp(PreparedStatement p,int i,Instant v)throws SQLException { if(v==null)p.setNull(i,Types.TIMESTAMP_WITH_TIMEZONE); else p.setTimestamp(i,Timestamp.from(v)); }
    private static void require(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");}
}
