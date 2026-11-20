package tech.kayys.numou.content.postgres;

import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;
import tech.kayys.numou.content.spi.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** PostgreSQL lease-based outbox with SKIP LOCKED and worker-owned acknowledgements. */
public final class PostgresEditorialOutboxRepository implements EditorialOutboxRepository {
    private final DataSource dataSource;
    public PostgresEditorialOutboxRepository(DataSource dataSource) { this.dataSource=Objects.requireNonNull(dataSource); }

    @Override public List<EditorialOutboxMessage> claim(UUID workerId, Instant now, Instant leaseUntil, int limit) {
        if(limit<1 || limit>500) throw new IllegalArgumentException("Batch limit must be between 1 and 500");
        String sql="""
            WITH candidates AS (
                SELECT tenant_id,event_id
                  FROM content_editorial_outbox
                 WHERE published_at IS NULL
                   AND available_at <= ?
                   AND (lease_until IS NULL OR lease_until < ?)
                 ORDER BY created_at,event_id
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE content_editorial_outbox o
               SET lease_owner=?, lease_until=?, attempts=o.attempts+1
              FROM candidates c
             WHERE o.tenant_id=c.tenant_id AND o.event_id=c.event_id
            RETURNING o.tenant_id,o.event_id,o.review_id,o.event_type,o.payload::text,o.attempts,o.created_at,(o.payload->>'contentId')::uuid,(o.payload->>'revision')::integer
            """;
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            List<EditorialOutboxMessage> messages=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement(sql)) {
                p.setTimestamp(1,Timestamp.from(now));p.setTimestamp(2,Timestamp.from(now));p.setInt(3,limit);
                p.setObject(4,workerId);p.setTimestamp(5,Timestamp.from(leaseUntil));
                try(ResultSet r=p.executeQuery()) { while(r.next()) {
                    String payload=r.getString(5);
                    messages.add(new EditorialOutboxMessage(new TenantId(r.getObject(1,UUID.class)),
                            r.getObject(2,UUID.class),new EditorialReviewId(r.getObject(3,UUID.class)),
                            new ContentId(r.getObject(8,UUID.class)),r.getInt(9),
                            r.getString(4),payload,r.getInt(6),r.getTimestamp(7).toInstant()));
                }}
            }
            c.commit(); return List.copyOf(messages);
        } catch(SQLException e) { throw failure("claim editorial outbox",e); }
    }

    @Override public boolean markPublished(TenantId tenantId,UUID eventId,UUID workerId,Instant publishedAt) {
        String sql="UPDATE content_editorial_outbox SET published_at=?,lease_owner=NULL,lease_until=NULL,last_error=NULL WHERE tenant_id=? AND event_id=? AND lease_owner=? AND published_at IS NULL";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            p.setTimestamp(1,Timestamp.from(publishedAt));p.setObject(2,tenantId.value());p.setObject(3,eventId);p.setObject(4,workerId);
            return p.executeUpdate()==1;
        } catch(SQLException e) { throw failure("acknowledge editorial outbox",e); }
    }

    @Override public boolean releaseForRetry(TenantId tenantId,UUID eventId,UUID workerId,Instant retryAt,String error) {
        String sql="UPDATE content_editorial_outbox SET available_at=?,lease_owner=NULL,lease_until=NULL,last_error=? WHERE tenant_id=? AND event_id=? AND lease_owner=? AND published_at IS NULL";
        String safe=error==null?"Unknown dispatch failure":error;
        if(safe.length()>2000)safe=safe.substring(0,2000);
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            p.setTimestamp(1,Timestamp.from(retryAt));p.setString(2,safe);p.setObject(3,tenantId.value());
            p.setObject(4,eventId);p.setObject(5,workerId);return p.executeUpdate()==1;
        } catch(SQLException e) { throw failure("release editorial outbox for retry",e); }
    }

    @Override public boolean markDeadLetter(TenantId tenantId,UUID eventId,UUID workerId,Instant failedAt,String error) {
        String sql="UPDATE content_editorial_outbox SET dead_lettered_at=?,lease_owner=NULL,lease_until=NULL,last_error=? WHERE tenant_id=? AND event_id=? AND lease_owner=? AND published_at IS NULL AND dead_lettered_at IS NULL";
        String safe=error==null?"Unknown dispatch failure":error;
        if(safe.length()>2000)safe=safe.substring(0,2000);
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            p.setTimestamp(1,Timestamp.from(failedAt));p.setString(2,safe);p.setObject(3,tenantId.value());
            p.setObject(4,eventId);p.setObject(5,workerId);return p.executeUpdate()==1;
        } catch(SQLException e) { throw failure("dead-letter editorial outbox",e); }
    }

    private RuntimeException failure(String operation,SQLException e){return new IllegalStateException("Unable to "+operation+" (SQLState="+e.getSQLState()+")",e);}
}
