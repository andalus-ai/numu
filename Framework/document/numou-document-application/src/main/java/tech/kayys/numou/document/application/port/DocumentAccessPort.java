package tech.kayys.syirkah.document.application.port;

import io.smallrye.mutiny.Uni;

public interface DocumentAccessPort {
    Uni<Void> require(String tenantId, String permission);
}
