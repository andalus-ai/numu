package tech.kayys.numou.identity.application.query;

import tech.kayys.numou.identity.domain.user.UserStatus;

import java.util.UUID;

/**
 * Read-side projection of a User - only the fields a consumer of the
 * query API needs. Never expose PasswordHash through this view.
 */
public record UserView(
        UUID userId,
        String email,
        String displayName,
        UserStatus status
) {
}
