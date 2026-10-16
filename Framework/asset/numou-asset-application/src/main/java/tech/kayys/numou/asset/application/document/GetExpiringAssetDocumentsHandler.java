package tech.kayys.numou.asset.application.document;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.document.AssetDocumentReference;
import tech.kayys.numou.asset.domain.repository.AssetDocumentReferenceRepository;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.Objects;

/** Read-side handler: references expiring within the requested window. */
@jakarta.enterprise.context.ApplicationScoped
public class GetExpiringAssetDocumentsHandler {

    private final AssetDocumentReferenceRepository references;
    private final DomainClock clock;

    public GetExpiringAssetDocumentsHandler(AssetDocumentReferenceRepository references, DomainClock clock) {
        this.references = Objects.requireNonNull(references, "references");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<List<AssetDocumentReference>> handle(GetExpiringAssetDocumentsQuery query) {
        return Uni.createFrom()
                .completionStage(() -> references.findExpiring(query.tenantId(), clock().now(), query.window()));
    }

    private DomainClock clock() {
        return clock;
    }
}
