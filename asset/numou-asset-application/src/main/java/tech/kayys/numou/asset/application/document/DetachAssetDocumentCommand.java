package tech.kayys.numou.asset.application.document;

import tech.kayys.numou.asset.domain.document.AssetDocumentReferenceId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/** Detach a document reference from an asset (ASSET-24 §19). */
public record DetachAssetDocumentCommand(
        String tenantId,
        AssetDocumentReferenceId referenceId
) implements Command {

    public DetachAssetDocumentCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(referenceId, "referenceId cannot be null");
    }
}
