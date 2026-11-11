package tech.kayys.numou.developer.apicatalog.integration.openapi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.developer.apicatalog.application.cleanup.ApiArtifactCleanupDispatcher;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactObjectDeletionPort;

/** Composition boundary for the application cleanup dispatcher. */
@ApplicationScoped
public class ApiArtifactCleanupDispatcherProducer {
    @Produces
    @ApplicationScoped
    ApiArtifactCleanupDispatcher dispatcher(ApiArtifactCleanupRepository repository,
                                            ApiArtifactObjectDeletionPort deletion) {
        return new ApiArtifactCleanupDispatcher(repository, deletion);
    }
}
