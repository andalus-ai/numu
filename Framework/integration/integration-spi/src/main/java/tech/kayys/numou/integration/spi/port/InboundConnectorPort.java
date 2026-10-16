package tech.kayys.syirkah.integration.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.integration.domain.identifier.ExternalSystemId;

import java.util.Map;

/**
 * Inbound adapter for partner pushes and pull-based connectors.
 *
 * <p>Implementations translate a partner's payload into internal
 * commands - the partner's wire format never reaches a domain model.
 */
public interface InboundConnectorPort {

    /**
     * Ingests one partner message.
     *
     * @param systemId      which registered system sent it
     * @param correlationId journey id, propagated end to end
     * @param headers       transport metadata (never trusted as domain input)
     * @param body          the raw payload as received
     * @return an idempotency key for the message, used to deduplicate replay
     */
    Uni<String> ingest(
            ExternalSystemId systemId,
            String correlationId,
            Map<String, String> headers,
            String body);
}
