package tech.kayys.numou.pos.application.port.out;

/**
 * Provider-neutral publication port. Implementations must use eventId as the
 * broker message key/id where supported; delivery is at-least-once.
 */
@FunctionalInterface
public interface PosEventPublisher {
    void publish(PosOutboxEvent event) throws Exception;
}
