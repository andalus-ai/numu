package tech.kayys.numou.identity.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A SCIM Group projected from a group->role mapping entry plus its current members.
 */
public record ScimGroup(
        String id,
        String displayName,
        UUID roleId,
        String scopePath,
        List<ScimGroupMember> members
) {

    public ScimGroup {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(roleId, "roleId cannot be null");
        Objects.requireNonNull(scopePath, "scopePath cannot be null");
        members = members == null ? List.of() : List.copyOf(members);
    }
}
