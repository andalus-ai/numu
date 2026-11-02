package tech.kayys.numou.event.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.application.command.ReplayDeadLetterCommand;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.spi.port.DeadLetterReplayAuthorizationPort;
import tech.kayys.numou.event.spi.port.DeadLetterReplayStorePort;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Authorized, audited and fenced replay of one dead-letter event. */
public final class DeadLetterReplayService {
    private final DeadLetterReplayAuthorizationPort authorization;
    private final DeadLetterReplayStorePort store;
    private final EventSerializerPort serializer;
    private final EventPublisherPort publisher;

    public DeadLetterReplayService(DeadLetterReplayAuthorizationPort authorization,
            DeadLetterReplayStorePort store, EventSerializerPort serializer, EventPublisherPort publisher) {
        this.authorization = Objects.requireNonNull(authorization);
        this.store = Objects.requireNonNull(store);
        this.serializer = Objects.requireNonNull(serializer);
        this.publisher = Objects.requireNonNull(publisher);
    }

    public Uni<ReplayResult> replay(ReplayDeadLetterCommand command) {
        Objects.requireNonNull(command, "command");
        return authorization.authorizeReplay(command.tenantId(), command.actorId(), command.deadLetterRecordId())
                .chain(() -> Uni.createFrom().item(() -> {
                    EventEnvelope<? extends BusinessEvent> envelope =
                            serializer.deserialize(command.payload(), command.eventType());
                    if (envelope == null || !command.eventType().equals(envelope.eventType())) {
                        throw new IllegalArgumentException("DLQ event type does not match its serialized envelope");
                    }
                    if (!command.tenantId().equals(envelope.metadata().tenantId())) {
                        throw new SecurityException("DLQ event tenant does not match the authorized tenant");
                    }
                    return envelope;
                }))
                .chain(envelope -> {
                    var request = new DeadLetterReplayStorePort.ReplayRequest(
                            command.deadLetterRecordId(), command.tenantId(), command.actorId(),
                            command.reason(), command.eventType(), sha256(command.payload()));
                    return store.beginReplay(request).chain(claim -> switch (claim.status()) {
                        case ALREADY_COMPLETED -> Uni.createFrom().item(
                                new ReplayResult(ReplayStatus.ALREADY_PUBLISHED, claim.attempt()));
                        case IN_PROGRESS -> Uni.createFrom().item(
                                new ReplayResult(ReplayStatus.IN_PROGRESS, claim.attempt()));
                        case ACQUIRED -> publisher.publish(envelope)
                                .onFailure().call(failure -> store.failReplay(claim, failureCode(failure)))
                                .chain(() -> store.completeReplay(claim))
                                .replaceWith(new ReplayResult(ReplayStatus.PUBLISHED, claim.attempt()));
                    });
                });
    }

    private static String failureCode(Throwable failure) {
        String code = failure.getClass().getSimpleName();
        return code.length() > 120 ? code.substring(0, 120) : code;
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public enum ReplayStatus { PUBLISHED, ALREADY_PUBLISHED, IN_PROGRESS }
    public record ReplayResult(ReplayStatus status, int attempt) { }
}
