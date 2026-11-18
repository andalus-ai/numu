package tech.kayys.numou.document.adapter.outbound.scanning;

import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.document.application.port.DocumentScanner;
import tech.kayys.numou.foundation.domain.time.DomainClock;

/**
 * Safe fallback used until a host installs a real scanner adapter.
 * It deliberately returns FAILED; it can never approve or expose uploaded content.
 */
@ApplicationScoped
@DefaultBean
public class UnavailableDocumentScanner implements DocumentScanner {
    private final DomainClock clock;

    @Inject
    public UnavailableDocumentScanner(DomainClock clock) {
        this.clock = clock;
    }

    @Override
    public Uni<ScanVerdict> scan(ScanRequest request) {
        return Uni.createFrom().item(new ScanVerdict(
                Outcome.FAILED,
                "unconfigured",
                "unavailable",
                "SCANNER_NOT_CONFIGURED",
                clock.now()
        ));
    }
}
