package tech.kayys.numou.history.graph.postgres;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionRepairAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRepairAuditStore.RepairOutcome;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL append-only audit writer for operator-triggered projection repairs. */
public final class JdbcHistoricalProjectionRepairAuditStore implements HistoricalProjectionRepairAuditStore {
    private final DataSource source;
    public JdbcHistoricalProjectionRepairAuditStore(DataSource source) {
        this.source=Objects.requireNonNull(source,"source");
    }

    @Override public UUID start(UUID tenantId,UUID actorId,int pageSize,Instant occurredAt) {
        if(pageSize<1||pageSize>200) throw new IllegalArgumentException("pageSize must be 1..200");
        UUID operationId=UUID.randomUUID();
        append(tenantId,actorId,operationId,"STARTED",pageSize,null,"",occurredAt);
        return operationId;
    }

    @Override public void complete(UUID tenantId,UUID actorId,UUID operationId,RepairOutcome outcome,Instant occurredAt) {
        Objects.requireNonNull(outcome,"outcome");
        append(tenantId,actorId,operationId,"COMPLETED",outcome.pageSize(),outcome,"",occurredAt);
    }

    @Override public void fail(UUID tenantId,UUID actorId,UUID operationId,int pageSize,String reason,Instant occurredAt) {
        append(tenantId,actorId,operationId,"FAILED",pageSize,null,safe(reason),occurredAt);
    }

    private void append(UUID tenantId,UUID actorId,UUID operationId,String phase,int pageSize,
                        RepairOutcome outcome,String detail,Instant occurredAt) {
        Objects.requireNonNull(actorId,"actorId"); Objects.requireNonNull(operationId,"operationId");
        Objects.requireNonNull(occurredAt,"occurredAt");
        String sql="""
            INSERT INTO historical_projection_repair_audit
              (audit_id,operation_id,tenant_id,actor_id,phase,page_size,canonical_checked,
               missing,stale,divergent,orphaned,repaired,detail,occurred_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try(Connection c=source.getConnection()) {
            c.setAutoCommit(false);
            if(tenantId!=null) try(PreparedStatement scope=c.prepareStatement(
                    "SELECT set_config('numou.tenant_id', ?, true)")) {
                scope.setString(1,tenantId.toString()); scope.execute();
            }
            try(PreparedStatement s=c.prepareStatement(sql)) {
                s.setObject(1,UUID.randomUUID()); s.setObject(2,operationId); s.setObject(3,tenantId);
                s.setObject(4,actorId); s.setString(5,phase); s.setInt(6,pageSize);
                s.setLong(7,outcome==null?0:outcome.canonicalChecked());
                s.setLong(8,outcome==null?0:outcome.missing()); s.setLong(9,outcome==null?0:outcome.stale());
                s.setLong(10,outcome==null?0:outcome.divergent()); s.setLong(11,outcome==null?0:outcome.orphaned());
                s.setLong(12,outcome==null?0:outcome.repaired()); s.setString(13,detail);
                s.setTimestamp(14,Timestamp.from(occurredAt)); s.executeUpdate();
            }
            c.commit();
        } catch(SQLException e) {
            throw new IllegalStateException("Unable to append historical projection repair audit",e);
        }
    }

    private static String safe(String reason) {
        String value=Objects.toString(reason,"projection repair failed").replaceAll("[\\r\\n\\t]+"," ");
        return value.length()>1000?value.substring(0,1000):value;
    }
}
