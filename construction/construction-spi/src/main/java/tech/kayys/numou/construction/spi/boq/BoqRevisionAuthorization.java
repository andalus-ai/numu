package tech.kayys.numou.construction.spi.boq;

import io.smallrye.mutiny.Uni;
import java.util.UUID;

/**
 * Fail-closed authorization port for BOQ revision commands.
 * Implementations must derive grants from trusted server-side identity/context,
 * never from request-body role claims. Prefer querying grants on the same
 * transaction/connection where the application's security model permits it.
 */
@FunctionalInterface
public interface BoqRevisionAuthorization {
    Uni<Void> authorize(String tenantId, UUID actorId, UUID boqId, Action action);

    enum Action { CAPTURE_DRAFT, SUBMIT_FOR_REVIEW, APPROVE }
}
