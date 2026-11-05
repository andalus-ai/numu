package tech.kayys.numou.organization.adapter.jdbc;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.application.InvitationSecretCipherPort;
import tech.kayys.numou.organization.application.OrganizationInvitationDeliveryOutboxPort;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** PostgreSQL queue adapter using atomic SKIP LOCKED claims and lease-token fencing. */
public final class JdbcOrganizationInvitationDeliveryOutbox implements OrganizationInvitationDeliveryOutboxPort {
    private final DataSource dataSource;
    private final Executor executor;

    public JdbcOrganizationInvitationDeliveryOutbox(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.executor = Objects.requireNonNull(executor);
    }

    @Override public CompletionStage<Void> enqueue(DeliveryMessage m) {
        Objects.requireNonNull(m);
        return async(() -> {
            String sql = "INSERT INTO organization_invitation_delivery_outbox "
                    + "(delivery_id, tenant_id, invitation_id, recipient_email, encryption_algorithm, "
                    + "encryption_format_version, encryption_key_id, vault_id, secret_id, secret_ciphertext, "
                    + "status, attempts, available_at, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?) "
                    + "ON CONFLICT (delivery_id) DO NOTHING";
            try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
                s.setObject(1, m.deliveryId());
                s.setObject(2, m.tenantId().value());
                s.setObject(3, m.invitationId().value());
                s.setString(4, m.recipientEmail());
                bindSecret(s, 5, m.secret());
                s.setTimestamp(12, Timestamp.from(m.createdAt()));
                s.setTimestamp(13, Timestamp.from(m.createdAt()));
                s.setTimestamp(14, Timestamp.from(m.createdAt()));
                s.executeUpdate();
                return null;
            } catch (SQLException e) { throw new IllegalStateException("Unable to enqueue invitation delivery", e); }
        });
    }

    @Override public CompletionStage<List<ClaimedDelivery>> claim(Instant now, Instant leaseUntil, int limit) {
        if (limit < 1 || limit > 100 || !leaseUntil.isAfter(now))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid claim bounds"));
        return async(() -> {
            String sql = "WITH candidates AS (SELECT delivery_id FROM organization_invitation_delivery_outbox "
                    + "WHERE (status='PENDING' AND available_at<=?) OR (status='LEASED' AND lease_until<=?) "
                    + "ORDER BY available_at, created_at, delivery_id FOR UPDATE SKIP LOCKED LIMIT ?) "
                    + "UPDATE organization_invitation_delivery_outbox o SET status='LEASED', "
                    + "lease_token=gen_random_uuid(), lease_until=?, attempts=attempts+1, updated_at=? "
                    + "FROM candidates c WHERE o.delivery_id=c.delivery_id RETURNING o.delivery_id, o.tenant_id, "
                    + "o.invitation_id, o.recipient_email, o.encryption_algorithm, o.encryption_format_version, "
                    + "o.encryption_key_id, o.vault_id, o.secret_id, o.secret_ciphertext, o.attempts, "
                    + "o.lease_token, o.lease_until";
            try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
                s.setTimestamp(1, Timestamp.from(now));
                s.setTimestamp(2, Timestamp.from(now));
                s.setInt(3, limit);
                s.setTimestamp(4, Timestamp.from(leaseUntil));
                s.setTimestamp(5, Timestamp.from(now));
                try (ResultSet rs = s.executeQuery()) {
                    List<ClaimedDelivery> result = new ArrayList<>();
                    while (rs.next()) result.add(new ClaimedDelivery(
                            rs.getObject("delivery_id", UUID.class),
                            new TenantId(rs.getObject("tenant_id", UUID.class)),
                            OrganizationInvitationId.of(rs.getObject("invitation_id", UUID.class)),
                            rs.getString("recipient_email"), readSecret(rs), rs.getInt("attempts"),
                            rs.getObject("lease_token", UUID.class), rs.getTimestamp("lease_until").toInstant()));
                    return List.copyOf(result);
                }
            } catch (SQLException e) { throw new IllegalStateException("Unable to claim invitation deliveries", e); }
        });
    }

    @Override public CompletionStage<Boolean> markSent(UUID id, TenantId tenant, UUID token, Instant at) {
        return transition("UPDATE organization_invitation_delivery_outbox SET status='SENT', sent_at=?, "
                + "lease_token=NULL, lease_until=NULL, updated_at=? WHERE delivery_id=? AND tenant_id=? "
                + "AND status='LEASED' AND lease_token=?", id, tenant, token, at, null);
    }

    @Override public CompletionStage<Boolean> retry(UUID id, TenantId tenant, UUID token, Instant availableAt, String code) {
        return transition("UPDATE organization_invitation_delivery_outbox SET status='PENDING', available_at=?, "
                + "last_error_code=?, lease_token=NULL, lease_until=NULL, updated_at=? WHERE delivery_id=? "
                + "AND tenant_id=? AND status='LEASED' AND lease_token=?", id, tenant, token, availableAt, code);
    }

    @Override public CompletionStage<Boolean> deadLetter(UUID id, TenantId tenant, UUID token, Instant at, String code) {
        return transition("UPDATE organization_invitation_delivery_outbox SET status='DEAD', failed_at=?, "
                + "last_error_code=?, lease_token=NULL, lease_until=NULL, updated_at=? WHERE delivery_id=? "
                + "AND tenant_id=? AND status='LEASED' AND lease_token=?", id, tenant, token, at, code);
    }

    private CompletionStage<Boolean> transition(String sql, UUID id, TenantId tenant, UUID token,
                                                  Instant when, String code) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
                s.setTimestamp(1, Timestamp.from(when));
                int i = 2;
                if (sql.contains("last_error_code")) s.setString(i++, safeCode(code));
                s.setTimestamp(i++, Timestamp.from(when));
                s.setObject(i++, id);
                s.setObject(i++, tenant.value());
                s.setObject(i, token);
                return s.executeUpdate() == 1;
            } catch (SQLException e) { throw new IllegalStateException("Unable to transition invitation delivery", e); }
        });
    }

    private static void bindSecret(PreparedStatement s, int i, InvitationSecretCipherPort.EncryptedSecret v)
            throws SQLException {
        s.setString(i, v.algorithm());
        s.setInt(i + 1, v.formatVersion());
        s.setString(i + 2, v.keyId());
        s.setObject(i + 3, UUID.fromString(v.vaultId()));
        s.setObject(i + 4, UUID.fromString(v.secretId()));
        s.setBytes(i + 5, v.ciphertext());
    }

    private static InvitationSecretCipherPort.EncryptedSecret readSecret(ResultSet rs) throws SQLException {
        return new InvitationSecretCipherPort.EncryptedSecret(rs.getString("encryption_algorithm"),
                rs.getInt("encryption_format_version"), rs.getString("encryption_key_id"),
                rs.getObject("vault_id", UUID.class).toString(), rs.getObject("secret_id", UUID.class).toString(),
                rs.getBytes("secret_ciphertext"));
    }

    private static String safeCode(String code) {
        return code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "DELIVERY_FAILED";
    }

    private <T> CompletionStage<T> async(Callable<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try { return work.call(); }
            catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new IllegalStateException("Invitation outbox operation failed", e); }
        }, executor);
    }
}
