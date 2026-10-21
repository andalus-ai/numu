package tech.kayys.numou.mobility.ride.domain;

import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.ride.domain.event.ChatMessageSent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The conversation between the rider and the driver of one ride. Keyed by the ride id (one chat per ride). Bounded on purpose: at most
 * 200 messages of at most 500 characters, and a sender cannot post more than once per second; this is coordination ("I'm at the gate"),
 * not a messenger. Message text is filtered by {@link ChatFilter} and is never placed in events or logs.
 */
public final class RideChat extends VersionedAggregate<RideId> {

    private static final long serialVersionUID = 1L;
    public static final int MAX_MESSAGES = 200;
    public static final int MAX_LENGTH = 500;
    public static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    public enum Sender { RIDER, DRIVER }

    public record Message(Sender sender, String text, Instant at) { }

    public record Snapshot(UUID rideId, List<Message> messages, Instant createdAt, Instant updatedAt, int version) { }

    private final List<Message> messages = new ArrayList<>();

    private RideChat(RideId id, Instant now) {
        super(id, now);
    }

    public static RideChat open(RideId rideId, Instant now) {
        return new RideChat(rideId, now);
    }

    public Message send(Sender sender, String raw, Instant now) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleViolation("Message is empty");
        }
        if (raw.length() > MAX_LENGTH) {
            throw new BusinessRuleViolation("Message is too long (max " + MAX_LENGTH + " characters)");
        }
        if (messages.size() >= MAX_MESSAGES) {
            throw new BusinessRuleViolation("This conversation has reached its message limit");
        }
        final var last = messages.stream().filter(m -> m.sender() == sender).reduce((a, b) -> b);
        if (last.isPresent() && Duration.between(last.get().at(), now).compareTo(MIN_INTERVAL) < 0) {
            throw new BusinessRuleViolation("You are sending messages too quickly");
        }
        final String text = ChatFilter.sanitize(raw);
        if (text.isBlank()) {
            throw new BusinessRuleViolation("Message is empty");
        }
        final var m = new Message(sender, text, now);
        messages.add(m);
        touch(now);
        raise(ChatMessageSent.at(now, id().value(), sender.name()));
        return m;
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), new ArrayList<>(messages), createdAt, updatedAt, version);
    }

    public static RideChat restore(Snapshot s) {
        final var c = new RideChat(RideId.of(s.rideId()), s.createdAt());
        c.messages.addAll(s.messages());
        c.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return c;
    }
}
