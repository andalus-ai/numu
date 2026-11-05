package tech.kayys.numou.organization.adapter.jdbc;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.*;
import tech.kayys.numou.organization.spi.OrganizationInvitationRepository;
import tech.kayys.numou.organization.application.OrganizationInvitationAcceptancePort;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class JdbcOrganizationInvitationRepository implements OrganizationInvitationRepository, OrganizationInvitationAcceptancePort {
    private static final String COLUMNS = "invitation_id, tenant_id, organization_id, recipient_email, role, "
            + "invited_by, token_hash, expires_at, status, accepted_by, accepted_at, revoked_at, "
            + "created_at, updated_at, version";
    private final DataSource dataSource;
    private final Executor jdbcExecutor;

    public JdbcOrganizationInvitationRepository(DataSource dataSource, Executor jdbcExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.jdbcExecutor = Objects.requireNonNull(jdbcExecutor);
    }

    @Override
    public CompletionStage<OrganizationInvitation> save(OrganizationInvitation invitation, TenantId tenantId) {
        Objects.requireNonNull(invitation);
        Objects.requireNonNull(tenantId);
        if (!invitation.tenantId().equals(tenantId)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Tenant mismatch"));
        }
        return CompletableFuture.supplyAsync(() -> saveBlocking(invitation, tenantId), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationInvitation>> findByTenantAndId(
            TenantId tenantId, OrganizationInvitationId invitationId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(invitationId);
        return query("SELECT " + COLUMNS + " FROM organization_invitations WHERE tenant_id = ? AND invitation_id = ?",
                s -> { s.setObject(1, tenantId.value()); s.setObject(2, invitationId.value()); });
    }

    @Override
    public CompletionStage<Optional<OrganizationInvitation>> findByTenantAndTokenHash(
            TenantId tenantId, String tokenHash) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tokenHash);
        return query("SELECT " + COLUMNS + " FROM organization_invitations "
                        + "WHERE tenant_id = ? AND token_hash = ?",
                s -> { s.setObject(1, tenantId.value()); s.setString(2, tokenHash); });
    }

    @Override
    public CompletionStage<Optional<OrganizationInvitation>> findPendingByTenantAndOrganizationAndEmail(
            TenantId tenantId, OrganizationId organizationId, String normalizedEmail) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(organizationId);
        Objects.requireNonNull(normalizedEmail);
        return query("SELECT " + COLUMNS + " FROM organization_invitations "
                        + "WHERE tenant_id = ? AND organization_id = ? AND recipient_email = ? "
                        + "AND status = 'PENDING'",
                s -> {
                    s.setObject(1, tenantId.value());
                    s.setObject(2, organizationId.value());
                    s.setString(3, normalizedEmail);
                });
    }

    @Override
    public CompletionStage<AcceptedInvitation> accept(TenantId tenantId, String tokenHash,
            UserId authenticatedUserId, String verifiedEmail, Instant acceptedAt) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tokenHash);
        Objects.requireNonNull(authenticatedUserId);
        Objects.requireNonNull(verifiedEmail);
        Objects.requireNonNull(acceptedAt);
        return CompletableFuture.supplyAsync(() -> acceptBlocking(
                tenantId, tokenHash, authenticatedUserId, verifiedEmail, acceptedAt), jdbcExecutor);
    }

    private AcceptedInvitation acceptBlocking(TenantId tenantId, String tokenHash,
            UserId authenticatedUserId, String verifiedEmail, Instant acceptedAt) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String select = "SELECT " + COLUMNS + " FROM organization_invitations "
                        + "WHERE tenant_id = ? AND token_hash = ? FOR UPDATE";
                OrganizationInvitation invitation;
                try (PreparedStatement s = c.prepareStatement(select)) {
                    s.setObject(1, tenantId.value());
                    s.setString(2, tokenHash);
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Invitation not found");
                        invitation = read(rs);
                    }
                }
                int expectedVersion = invitation.getVersion();
                invitation.accept(authenticatedUserId, verifiedEmail, acceptedAt);
                update(c, invitation, tenantId, expectedVersion);

                OrganizationMembership membership = OrganizationMembership.invite(
                        OrganizationMembershipId.generate(), invitation.organizationId(),
                        authenticatedUserId, invitation.role());
                membership.activate();
                membership.setCreatedAt(acceptedAt);
                membership.setUpdatedAt(acceptedAt);
                String insertMembership = "INSERT INTO organization_memberships "
                        + "(membership_id, tenant_id, organization_id, user_id, status, role, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement s = c.prepareStatement(insertMembership)) {
                    s.setObject(1, membership.getId().value());
                    s.setObject(2, tenantId.value());
                    s.setObject(3, invitation.organizationId().value());
                    s.setObject(4, authenticatedUserId.value());
                    s.setString(5, membership.status().name());
                    s.setString(6, membership.role().name());
                    s.setLong(7, membership.getVersion());
                    s.setTimestamp(8, Timestamp.from(acceptedAt));
                    s.setTimestamp(9, Timestamp.from(acceptedAt));
                    s.executeUpdate();
                }
                c.commit();
                return new AcceptedInvitation(invitation.getId(), membership.getId(),
                        invitation.organizationId(), authenticatedUserId, invitation.role());
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                if (e instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new IllegalStateException("Invitation already consumed or membership already exists", sql);
                }
                throw new IllegalStateException("Unable to accept organization invitation", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open invitation acceptance transaction", e);
        }
    }

    private OrganizationInvitation saveBlocking(OrganizationInvitation candidate, TenantId tenantId) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Optional<OrganizationInvitation> current = findForUpdate(c, tenantId, candidate.getId());
                if (current.isEmpty()) {
                    if (candidate.getVersion() != 0) throw new IllegalStateException("New invitation must start at version zero");
                    insert(c, candidate, tenantId);
                } else {
                    OrganizationInvitation old = current.get();
                    if (candidate.getVersion() == old.getVersion()
                            && candidate.status() == old.status()
                            && candidate.tokenHash().equals(old.tokenHash())
                            && candidate.expiresAt().equals(old.expiresAt())
                            && Objects.equals(candidate.acceptedBy(), old.acceptedBy())
                            && Objects.equals(candidate.acceptedAt(), old.acceptedAt())
                            && Objects.equals(candidate.revokedAt(), old.revokedAt())) {
                        c.commit();
                        return old;
                    }
                    if (candidate.getVersion() != old.getVersion() + 1) {
                        throw new IllegalStateException("Invitation version conflict");
                    }
                    if (!old.organizationId().equals(candidate.organizationId())
                            || !old.recipientEmail().equals(candidate.recipientEmail())
                            || !old.invitedBy().equals(candidate.invitedBy())
                            || !old.getCreatedAt().equals(candidate.getCreatedAt())) {
                        throw new IllegalArgumentException("Invitation identity fields are immutable");
                    }
                    update(c, candidate, tenantId, old.getVersion());
                }
                c.commit();
                return candidate;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to save organization invitation", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open invitation transaction", e);
        }
    }

    private Optional<OrganizationInvitation> findForUpdate(Connection c, TenantId tenantId,
            OrganizationInvitationId id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + COLUMNS
                + " FROM organization_invitations WHERE tenant_id = ? AND invitation_id = ? FOR UPDATE")) {
            s.setObject(1, tenantId.value());
            s.setObject(2, id.value());
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? Optional.of(read(rs)) : Optional.empty(); }
        }
    }

    private void insert(Connection c, OrganizationInvitation v, TenantId tenantId) throws SQLException {
        String sql = "INSERT INTO organization_invitations (" + COLUMNS + ") VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, v, tenantId);
            s.executeUpdate();
        }
    }

    private void update(Connection c, OrganizationInvitation v, TenantId tenantId, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE organization_invitations SET token_hash = ?, expires_at = ?, status = ?, "
                + "accepted_by = ?, accepted_at = ?, revoked_at = ?, updated_at = ?, version = ? "
                + "WHERE tenant_id = ? AND invitation_id = ? AND version = ?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, v.tokenHash());
            s.setTimestamp(2, Timestamp.from(v.expiresAt()));
            s.setString(3, v.status().name());
            s.setObject(4, v.acceptedBy() == null ? null : v.acceptedBy().value());
            s.setTimestamp(5, v.acceptedAt() == null ? null : Timestamp.from(v.acceptedAt()));
            s.setTimestamp(6, v.revokedAt() == null ? null : Timestamp.from(v.revokedAt()));
            s.setTimestamp(7, Timestamp.from(v.getUpdatedAt()));
            s.setInt(8, v.getVersion());
            s.setObject(9, tenantId.value());
            s.setObject(10, v.getId().value());
            s.setInt(11, expectedVersion);
            if (s.executeUpdate() != 1) throw new IllegalStateException("Invitation version conflict");
        }
    }

    private void bind(PreparedStatement s, OrganizationInvitation v, TenantId tenantId) throws SQLException {
        s.setObject(1, v.getId().value());
        s.setObject(2, tenantId.value());
        s.setObject(3, v.organizationId().value());
        s.setString(4, v.recipientEmail());
        s.setString(5, v.role().name());
        s.setObject(6, v.invitedBy().value());
        s.setString(7, v.tokenHash());
        s.setTimestamp(8, Timestamp.from(v.expiresAt()));
        s.setString(9, v.status().name());
        s.setObject(10, v.acceptedBy() == null ? null : v.acceptedBy().value());
        s.setTimestamp(11, v.acceptedAt() == null ? null : Timestamp.from(v.acceptedAt()));
        s.setTimestamp(12, v.revokedAt() == null ? null : Timestamp.from(v.revokedAt()));
        s.setTimestamp(13, Timestamp.from(v.getCreatedAt()));
        s.setTimestamp(14, Timestamp.from(v.getUpdatedAt()));
        s.setInt(15, v.getVersion());
    }

    private CompletionStage<Optional<OrganizationInvitation>> query(String sql, Binder binder) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
                binder.bind(s);
                try (ResultSet rs = s.executeQuery()) { return rs.next() ? Optional.of(read(rs)) : Optional.empty(); }
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to query organization invitation", e);
            }
        }, jdbcExecutor);
    }

    private static OrganizationInvitation read(ResultSet rs) throws SQLException {
        return OrganizationInvitation.restore(
                OrganizationInvitationId.of(rs.getObject("invitation_id", UUID.class)),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                OrganizationId.of(rs.getObject("organization_id", UUID.class)),
                rs.getString("recipient_email"),
                OrganizationMembership.Role.valueOf(rs.getString("role")),
                new UserId(rs.getObject("invited_by", UUID.class)),
                rs.getString("token_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                OrganizationInvitationStatus.valueOf(rs.getString("status")),
                rs.getObject("accepted_by", UUID.class) == null ? null : new UserId(rs.getObject("accepted_by", UUID.class)),
                rs.getTimestamp("accepted_at") == null ? null : rs.getTimestamp("accepted_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getInt("version"));
    }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
}