package tech.kayys.numou.developer.apicatalog.application.command;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.developer.apicatalog.application.port.ApiCatalogAuthorizationPort;
import tech.kayys.numou.developer.apicatalog.application.port.ApiProductRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiSpecificationArtifactStore;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.result.ApplicationError;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Authorizes, bounds and hashes an OpenAPI upload before delegating to the storage port. */
public final class UploadApiSpecificationCommandHandler
        implements CommandHandler<UploadApiSpecificationCommand, ApiSpecificationArtifactStore.UploadedApiSpecificationArtifact> {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> CONTENT_TYPES = Set.of(
            "application/yaml", "application/x-yaml", "text/yaml", "application/json");

    private final ApiProductRepository repository;
    private final ApiCatalogAuthorizationPort authorization;
    private final ExecutionContextProvider contexts;
    private final ApiSpecificationArtifactStore artifacts;

    public UploadApiSpecificationCommandHandler(ApiProductRepository repository,
                                                ApiCatalogAuthorizationPort authorization,
                                                ExecutionContextProvider contexts,
                                                ApiSpecificationArtifactStore artifacts) {
        this.repository = Objects.requireNonNull(repository);
        this.authorization = Objects.requireNonNull(authorization);
        this.contexts = Objects.requireNonNull(contexts);
        this.artifacts = Objects.requireNonNull(artifacts);
    }

    @Override
    public Uni<ApiSpecificationArtifactStore.UploadedApiSpecificationArtifact> handle(
            UploadApiSpecificationCommand command) {
        var context = contexts.current();
        var tenant = context.requireTenant();
        byte[] content = command.content();
        String contentType = command.contentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (content.length == 0 || content.length > MAX_BYTES) {
            return Uni.createFrom().failure(ApplicationError.of("API_SPECIFICATION_SIZE_INVALID",
                    "OpenAPI specification must be between 1 byte and 10 MiB").toException());
        }
        if (!CONTENT_TYPES.contains(contentType)) {
            return Uni.createFrom().failure(ApplicationError.of("API_SPECIFICATION_MEDIA_TYPE_UNSUPPORTED",
                    "OpenAPI specification must be YAML or JSON").toException());
        }
        if (command.version().length() > 80
                || !command.version().matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?")) {
            return Uni.createFrom().failure(ApplicationError.of("API_VERSION_INVALID",
                    "API version must be a semantic version").toException());
        }
        final String digest = sha256(content);
        return authorization.requireCanManage(context, tenant)
                .chain(() -> repository.findById(tenant,
                        tech.kayys.numou.developer.apicatalog.domain.ApiProductId.of(command.productId())))
                .chain(found -> {
                    var product = found.orElseThrow(() -> ApplicationError.of("API_PRODUCT_NOT_FOUND",
                            "API product does not exist in this provider tenant").toException());
                    if (product.status() == tech.kayys.numou.developer.apicatalog.domain.ApiProductStatus.RETIRED) {
                        return Uni.createFrom().failure(ApplicationError.of("API_PRODUCT_RETIRED",
                                "Cannot upload a specification for a retired API product").toException());
                    }
                    if (product.versions().containsKey(command.version())) {
                        return Uni.createFrom().failure(ApplicationError.of("API_VERSION_ALREADY_EXISTS",
                                "API version already exists; specification artifacts are immutable").toException());
                    }
                    return artifacts.store(tenant, product.id().value().toString(), command.version(),
                            content, contentType, digest);
                });
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
