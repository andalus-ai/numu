package tech.kayys.numou.identity.application.dto;

public record LoginRequest(
        String username,
        String password
) {}
