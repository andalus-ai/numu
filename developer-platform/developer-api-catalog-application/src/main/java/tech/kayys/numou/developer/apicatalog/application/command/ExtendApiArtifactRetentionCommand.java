package tech.kayys.numou.developer.apicatalog.application.command;

import java.time.Instant;
import java.util.UUID;

public record ExtendApiArtifactRetentionCommand(UUID productId, String version, Instant retentionUntil,
                                                 String reason) { }
