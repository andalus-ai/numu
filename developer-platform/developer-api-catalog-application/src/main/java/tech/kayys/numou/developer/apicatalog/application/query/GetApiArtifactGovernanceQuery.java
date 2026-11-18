package tech.kayys.numou.developer.apicatalog.application.query;

import java.util.UUID;

public record GetApiArtifactGovernanceQuery(UUID productId, String version) { }
