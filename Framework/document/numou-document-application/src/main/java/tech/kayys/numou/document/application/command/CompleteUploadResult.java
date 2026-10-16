package tech.kayys.syirkah.document.application.command;

import tech.kayys.syirkah.accounting.domain.document.DocumentId;
import tech.kayys.syirkah.accounting.domain.document.DocumentVersionId;

public record CompleteUploadResult(DocumentId documentId, DocumentVersionId versionId, int versionNumber) {}
