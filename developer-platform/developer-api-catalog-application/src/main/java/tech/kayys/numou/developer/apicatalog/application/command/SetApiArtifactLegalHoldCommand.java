package tech.kayys.numou.developer.apicatalog.application.command;

import java.util.UUID;

public record SetApiArtifactLegalHoldCommand(UUID productId, String version, boolean held, String reason) { }
