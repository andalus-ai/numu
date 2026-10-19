package tech.kayys.numou.asset.application.document;

import tech.kayys.numou.asset.domain.document.AssetDocumentReferenceId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/** Designate one reference as the primary document of its type (ASSET-24 §19/§21). */
public record SetPrimaryAssetDocumentCommand(
        String tenantId,
        AssetDocumentReferenceId referenceId
) implements Command {

    public SetPrimaryAssetDocumentCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(referenceId, "referenceId cannot be null");
    }
}
