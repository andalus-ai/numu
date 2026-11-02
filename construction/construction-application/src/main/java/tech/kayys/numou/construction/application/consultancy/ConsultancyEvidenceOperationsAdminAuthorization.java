package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import java.util.UUID;

/** Host-provided policy for privileged operational inspection and recovery. */
public interface ConsultancyEvidenceOperationsAdminAuthorization {
    Uni<Void> requireOperationsAccess(String tenantId, UUID actorId, Action action);
    enum Action { VIEW, VIEW_RECOVERY_HISTORY, RECOVER_TERMINAL_FAILURE }
}
