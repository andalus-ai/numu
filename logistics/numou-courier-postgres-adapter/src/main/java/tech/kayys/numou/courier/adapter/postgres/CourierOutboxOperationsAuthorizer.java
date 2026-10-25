package tech.kayys.numou.courier.adapter.postgres;

/** Deployment security adapter for outbox operations. Implementations must validate the authenticated actor. */
public interface CourierOutboxOperationsAuthorizer {
    boolean mayInspect(String actorReference, String destination);
    boolean mayReplay(String actorReference, String destination, String tenantId, String eventId);
}
