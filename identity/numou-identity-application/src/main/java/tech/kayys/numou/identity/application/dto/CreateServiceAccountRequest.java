package tech.kayys.numou.identity.application.dto;

import java.util.UUID;

public record CreateServiceAccountRequest(
        String name,
        String description,
        UUID ownerUserId
) {}
