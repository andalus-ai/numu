package tech.kayys.numou.queuemanagement.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record QueueId(UUID value) implements Serializable {
    public QueueId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static QueueId generate() {
        return new QueueId(UUID.randomUUID());
    }

    public static QueueId of(UUID value) {
        return new QueueId(value);
    }
}
