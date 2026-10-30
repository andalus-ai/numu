package tech.kayys.numou.workspace.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped directory boundary for Workspace mentions.
 * Implementations must return active members only and must never search across tenants.
 */
public interface WorkspaceMentionDirectory {
    Optional<Member> findActiveByHandle(UUID tenantId, String normalizedHandle);

    Optional<Member> findActiveByUserId(UUID tenantId, UUID userId);

    List<Member> searchActive(UUID tenantId, String normalizedQuery, int limit);

    record Member(UUID userId, String handle, String displayName) {
        public Member {
            java.util.Objects.requireNonNull(userId, "userId is required");
            if (handle == null || !handle.matches("[A-Za-z0-9](?:[A-Za-z0-9._-]{0,62}[A-Za-z0-9])?"))
                throw new IllegalArgumentException("handle must be a valid mention handle");
            if (displayName == null || displayName.isBlank() || displayName.length() > 160)
                throw new IllegalArgumentException("displayName must contain 1..160 characters");
            handle = handle.toLowerCase(java.util.Locale.ROOT);
            displayName = displayName.trim();
        }
    }
}
