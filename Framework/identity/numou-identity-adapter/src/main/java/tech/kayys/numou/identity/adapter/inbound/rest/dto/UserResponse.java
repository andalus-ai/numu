package tech.kayys.syirkah.identity.adapter.inbound.rest.dto;

import tech.kayys.syirkah.identity.application.query.UserView;
import tech.kayys.syirkah.identity.domain.user.UserStatus;

import java.util.UUID;

public record UserResponse(
        UUID userId,
        String email,
        String displayName,
        UserStatus status
) {

    public static UserResponse from(UserView view) {
        return new UserResponse(
                view.userId(),
                view.email(),
                view.displayName(),
                view.status()
        );
    }

}
