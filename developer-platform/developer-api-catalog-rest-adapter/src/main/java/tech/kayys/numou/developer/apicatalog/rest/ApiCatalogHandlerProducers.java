package tech.kayys.numou.developer.apicatalog.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.developer.apicatalog.application.command.*;
import tech.kayys.numou.developer.apicatalog.application.port.*;
import tech.kayys.numou.developer.apicatalog.application.query.*;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

/** Composition boundary for the catalog use cases exposed by this REST adapter. */
@ApplicationScoped
public class ApiCatalogHandlerProducers {
    @Inject ApiProductRepository repository;
    @Inject ApiCatalogAuthorizationPort authorization;
    @Inject ExecutionContextProvider executionContext;
    @Inject UnitOfWork unitOfWork;
    @Inject EventPublisher events;
    @Inject ApiSpecificationValidationPort specificationValidation;
    @Inject ApiSpecificationArtifactStore specificationArtifacts;

    @Produces @ApplicationScoped
    CreateApiProductCommandHandler createProduct() {
        return new CreateApiProductCommandHandler(repository, authorization, executionContext, unitOfWork, events);
    }

    @Produces @ApplicationScoped
    ListApiProductsQueryHandler listProducts() {
        return new ListApiProductsQueryHandler(repository, authorization, executionContext);
    }

    @Produces @ApplicationScoped
    GetApiSpecificationArtifactQueryHandler getSpecificationArtifact() {
        return new GetApiSpecificationArtifactQueryHandler(repository, authorization, executionContext, specificationArtifacts);
    }

    @Produces @ApplicationScoped
    GetApiProductQueryHandler getProduct() {
        return new GetApiProductQueryHandler(repository, authorization, executionContext);
    }

    @Produces @ApplicationScoped
    AddApiVersionCommandHandler addVersion() {
        return new AddApiVersionCommandHandler(repository, authorization, executionContext, unitOfWork);
    }

    @Produces @ApplicationScoped
    PublishApiVersionCommandHandler publishVersion() {
        return new PublishApiVersionCommandHandler(repository, authorization, executionContext, unitOfWork, events, specificationValidation);
    }

    @Produces @ApplicationScoped
    UploadApiSpecificationCommandHandler uploadSpecification() {
        return new UploadApiSpecificationCommandHandler(repository, authorization, executionContext, specificationArtifacts);
    }

    @Produces @ApplicationScoped
    DeprecateApiVersionCommandHandler deprecateVersion() {
        return new DeprecateApiVersionCommandHandler(repository, authorization, executionContext, unitOfWork);
    }

    @Produces @ApplicationScoped
    RetireApiVersionCommandHandler retireVersion() {
        return new RetireApiVersionCommandHandler(repository, authorization, executionContext, unitOfWork);
    }
}
