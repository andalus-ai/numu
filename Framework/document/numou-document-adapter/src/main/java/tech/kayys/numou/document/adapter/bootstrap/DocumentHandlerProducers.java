package tech.kayys.syirkah.document.adapter.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.syirkah.document.application.command.CompleteUploadHandler;
import tech.kayys.syirkah.document.application.command.CreateUploadSessionHandler;
import tech.kayys.syirkah.document.application.port.DocumentAccessPort;
import tech.kayys.syirkah.document.application.port.DocumentOutboxPort;
import tech.kayys.syirkah.document.application.port.DocumentRepository;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;
import tech.kayys.syirkah.document.application.port.UploadSessionRepository;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

@ApplicationScoped
public class DocumentHandlerProducers {
    @Inject
    DocumentAccessPort documentAccess;

    @Inject
    DocumentStoragePort storage;

    @Inject
    DocumentRepository documents;

    @Inject
    UploadSessionRepository uploadSessions;

    @Inject
    DocumentOutboxPort outbox;

    @Inject
    UnitOfWork unitOfWork;

    @Inject
    DomainClock clock;

    @Produces
    @ApplicationScoped
    public CreateUploadSessionHandler createUploadSessionHandler() {
        return new CreateUploadSessionHandler(documentAccess, storage, uploadSessions, clock);
    }

    @Produces
    @ApplicationScoped
    public CompleteUploadHandler completeUploadHandler() {
        return new CompleteUploadHandler(
                documentAccess, storage, documents, uploadSessions, outbox, unitOfWork, clock
        );
    }
}
