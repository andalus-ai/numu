package tech.kayys.syirkah.document.adapter.outbound.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import tech.kayys.syirkah.document.adapter.outbound.postgres.DocumentOutboxMessage;

@ApplicationScoped
public class DocumentOutboxPublisher {
    private final MutinyEmitter<String> emitter;
    private final ObjectMapper objectMapper;

    @Inject
    public DocumentOutboxPublisher(
            @Channel("document-events") MutinyEmitter<String> emitter,
            ObjectMapper objectMapper
    ) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    public Uni<Void> publish(DocumentOutboxMessage message) {
        try {
            var envelope = objectMapper.createObjectNode();
            envelope.put("eventId", message.eventId().toString());
            envelope.put("tenantId", message.tenantId().toString());
            envelope.put("aggregateType", message.aggregateType());
            envelope.put("aggregateId", message.aggregateId());
            envelope.put("eventType", message.eventType());
            envelope.put("occurredAt", message.occurredAt().toString());
            envelope.set("payload", objectMapper.readTree(message.payload()));
            return emitter.send(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException failure) {
            return Uni.createFrom().failure(new IllegalStateException(
                    "Could not serialize document outbox envelope " + message.eventId(), failure
            ));
        }
    }
}
