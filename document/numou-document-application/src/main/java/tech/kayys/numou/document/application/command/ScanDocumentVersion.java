package tech.kayys.numou.document.application.command;

public record ScanDocumentVersion(String tenantId, String documentId, String documentVersionId) {
    public ScanDocumentVersion {
        if (tenantId == null || tenantId.isBlank()
                || documentId == null || documentId.isBlank()
                || documentVersionId == null || documentVersionId.isBlank()) {
            throw new IllegalArgumentException("tenantId, documentId and documentVersionId are required");
        }
    }
}
