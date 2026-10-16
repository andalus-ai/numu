package tech.kayys.syirkah.accounting.domain.document;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Metadata attributes describing a document file. */
public record DocumentMetadata(
        String filename,
        String contentType,
        long fileSize,
        Map<String, String> customAttributes
) {
    public DocumentMetadata {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(contentType, "contentType");
        if (filename.isBlank() || contentType.isBlank()) {
            throw new IllegalArgumentException("filename and contentType must not be blank");
        }
        if (fileSize < 0) throw new IllegalArgumentException("fileSize must not be negative");
        customAttributes = customAttributes == null ? Map.of() : Map.copyOf(customAttributes);
    }

    public static DocumentMetadata of(String filename, String contentType, long fileSize) {
        return new DocumentMetadata(filename, contentType, fileSize, Map.of());
    }
}
