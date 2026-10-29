package tech.kayys.numou.queuemanagement.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record QueueEntryId(UUID value) implements Serializable {
    public QueueEntryId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static QueueEntryId generate() {
        return new QueueEntryId(UUID.randomUUID());
    }

    public static QueueEntryId of(UUID value) {
        return new QueueEntryId(value);
    }
}
