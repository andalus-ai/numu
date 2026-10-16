package tech.kayys.syirkah.document.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.document.UploadSession;
import tech.kayys.syirkah.accounting.domain.document.UploadSessionId;
import tech.kayys.syirkah.document.application.port.DocumentAccessPort;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;
import tech.kayys.syirkah.document.application.port.UploadSessionRepository;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.time.Duration;
import java.util.Objects;

public final class CreateUploadSessionHandler {
    private static final Duration MAX_LIFETIME = Duration.ofHours(24);

    private final DocumentAccessPort access;
    private final DocumentStoragePort storage;
    private final UploadSessionRepository sessions;
    private final DomainClock clock;

    public CreateUploadSessionHandler(
            DocumentAccessPort access,
            DocumentStoragePort storage,
            UploadSessionRepository sessions,
            DomainClock clock
    ) {
        this.access = Objects.requireNonNull(access);
        this.storage = Objects.requireNonNull(storage);
        this.sessions = Objects.requireNonNull(sessions);
        this.clock = Objects.requireNonNull(clock);
    }

    public Uni<CreateUploadSessionResult> handle(CreateUploadSession command) {
        Objects.requireNonNull(command, "command cannot be null");
        requireText(command.tenantId(), "tenantId");
        Objects.requireNonNull(command.documentType(), "documentType cannot be null");
        Objects.requireNonNull(command.classification(), "classification cannot be null");
        Objects.requireNonNull(command.metadata(), "metadata cannot be null");
        var lifetime = command.lifetime() == null ? Duration.ofMinutes(15) : command.lifetime();
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Upload session lifetime must be between 0 and 24 hours")
            );
        }

        var now = clock.now();
        var expiresAt = now.plus(lifetime);
        var sessionId = UploadSessionId.generate();
        return access.require(command.tenantId(), "document.create")
                .chain(() -> storage.createUploadTarget(new DocumentStoragePort.UploadTargetRequest(
                        command.tenantId(),
                        sessionId.value(),
                        command.metadata().filename(),
                        command.metadata().contentType(),
                        command.metadata().fileSize(),
                        command.expectedSha256(),
                        expiresAt
                )))
                .chain(target -> {
                    if (target.storageKey() == null || target.storageKey().isBlank()
                            || target.uploadUrl() == null || target.uploadUrl().isBlank()
                            || target.expiresAt() == null) {
                        return Uni.createFrom().failure(
                                new IllegalStateException("Storage returned an incomplete upload target")
                        );
                    }
                    if (!target.expiresAt().isAfter(now) || target.expiresAt().isAfter(expiresAt)) {
                        return Uni.createFrom().failure(
                                new IllegalStateException("Storage returned an invalid upload target expiry")
                        );
                    }
                    var session = UploadSession.create(
                            sessionId,
                            command.tenantId(),
                            target.storageKey(),
                            command.documentType(),
                            command.classification(),
                            command.metadata(),
                            command.expectedSha256(),
                            target.expiresAt()
                    );
                    return sessions.save(session)
                            .onFailure().call(failure -> storage.delete(target.storageKey()))
                            .replaceWith(new CreateUploadSessionResult(
                                    sessionId, target.uploadUrl(), target.expiresAt(), target.requiredHeaders()
                            ));
                });
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }
}
