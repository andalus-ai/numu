package tech.kayys.syirkah.accounting.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record JournalEntryId(UUID value) implements DomainId<UUID> {
    public JournalEntryId {
        Objects.requireNonNull(value, "JournalEntryId value cannot be null");
    }
    public UUID getValue() { return value; }
    public static JournalEntryId generate() {
        return new JournalEntryId(UUID.randomUUID());
    }
    public static JournalEntryId of(UUID value) {
        return new JournalEntryId(value);
    }
    public static JournalEntryId of(String value) {
        return new JournalEntryId(UUID.fromString(value));
    }
    public static JournalEntryId fromString(String value) {
        return new JournalEntryId(UUID.fromString(value));
    }
}
