package tech.kayys.numou.support.domain.ticket;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

public sealed interface Requester extends ValueObject
        permits Requester.Party, Requester.Anonymous {

    record Party(ParticipantId participantId, RequesterRole role) implements Requester {
        public Party {
            Objects.requireNonNull(participantId, "participantId cannot be null");
            Objects.requireNonNull(role, "role cannot be null");
        }
    }

    record Anonymous(String externalReference) implements Requester {
        public Anonymous {
            if (externalReference == null || externalReference.isBlank()) {
                throw new IllegalArgumentException("externalReference cannot be blank");
            }
            externalReference = externalReference.trim();
        }
    }
}
