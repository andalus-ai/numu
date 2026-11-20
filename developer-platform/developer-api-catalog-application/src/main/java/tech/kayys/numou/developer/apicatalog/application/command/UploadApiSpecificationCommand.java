package tech.kayys.numou.developer.apicatalog.application.command;

import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

/** Uploads immutable OpenAPI bytes for a draft version. */
public record UploadApiSpecificationCommand(UUID productId, String version,
                                            byte[] content, String contentType) implements Command {
    public UploadApiSpecificationCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(contentType, "contentType cannot be null");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
