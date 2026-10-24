package tech.kayys.numou.omnichannel.core;

/** Adapter to the host application's authorization/RBAC policy. */
@FunctionalInterface
public interface DeadLetterReplayAuthorizer {
    boolean mayReplay(String actorId, InboundEventKey key);
}
