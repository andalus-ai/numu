package tech.kayys.syirkah.document.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.document.UploadSession;
import tech.kayys.syirkah.accounting.domain.document.UploadSessionId;

import java.util.Optional;

public interface UploadSessionRepository {
    Uni<Optional<UploadSession>> findById(String tenantId, UploadSessionId uploadSessionId);

    default Uni<Optional<UploadSession>> findByIdForUpdate(
            String tenantId,
            UploadSessionId uploadSessionId
    ) {
        return findById(tenantId, uploadSessionId);
    }

    Uni<Void> save(UploadSession uploadSession);
}
