package tech.kayys.numou.asset.application.document;

import tech.kayys.numou.asset.domain.document.AssetDocumentType;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Attach an existing Documents-capability document to an asset (ASSET-24 §19). */
public record AttachAssetDocumentCommand(
        String tenantId,
        AssetId assetId,
        UUID documentId,
        Integer documentVersion,
        AssetDocumentType type,
        String title,
        boolean primary,
        boolean required,
        Instant validFrom,
        Instant validUntil,
        String linkedBy
) implements Command {

    public AttachAssetDocumentCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil cannot be before validFrom");
        }
    }
}
