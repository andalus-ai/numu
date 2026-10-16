package tech.kayys.numou.document.application.command;

import tech.kayys.numou.accounting.domain.document.DocumentId;
import tech.kayys.numou.accounting.domain.document.DocumentVersionId;

public record CompleteUploadResult(DocumentId documentId, DocumentVersionId versionId, int versionNumber) {}
