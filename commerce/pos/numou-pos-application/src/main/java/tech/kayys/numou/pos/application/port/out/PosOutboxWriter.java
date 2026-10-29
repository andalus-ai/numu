package tech.kayys.numou.pos.application.port.out;

/**
 * Persistence port for durable POS integration events.
 *
 * <p>Implementations must join the active business transaction. Appending an
 * event is not permission to publish it synchronously; a dispatcher may only
 * publish committed rows and must make delivery idempotent.</p>
 */
@FunctionalInterface
public interface PosOutboxWriter {
    void append(PosOutboxEvent event);
}
