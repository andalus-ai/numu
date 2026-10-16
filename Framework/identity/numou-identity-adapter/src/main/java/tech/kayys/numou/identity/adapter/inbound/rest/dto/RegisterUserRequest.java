package tech.kayys.syirkah.identity.adapter.inbound.rest.dto;

public record RegisterUserRequest(
        String email,
        String password,
        String displayName
) {
}
