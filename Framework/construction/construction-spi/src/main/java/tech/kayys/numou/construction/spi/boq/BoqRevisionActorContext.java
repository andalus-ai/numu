package tech.kayys.syirkah.construction.spi.boq;

import java.util.UUID;

/**
 * Supplies the authenticated actor from server-side security context.
 * Implementations must fail closed when no authenticated principal is present;
 * they must never read actor identity from command/request payload fields.
 */
@FunctionalInterface
public interface BoqRevisionActorContext {
    UUID requireActorId();
}
