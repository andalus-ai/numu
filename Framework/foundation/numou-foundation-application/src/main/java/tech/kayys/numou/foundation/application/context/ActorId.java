package tech.kayys.numou.foundation.application.context;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;

/**
 * Identifier of the executing actor (human user, service account, or system background process).
 */
public record ActorId(String value) implements DomainId<String> {

    public ActorId {
        Objects.requireNonNull(value, "ActorId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ActorId value cannot be blank");
        }
    }

    public static ActorId of(String value) {
        return new ActorId(value);
    }

    public static ActorId system() {
        return new ActorId("system");
    }

    public static ActorId anonymous() {
        return new ActorId("anonymous");
    }
}
