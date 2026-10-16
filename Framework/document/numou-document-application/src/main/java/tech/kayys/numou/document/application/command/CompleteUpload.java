package tech.kayys.syirkah.document.application.command;

import tech.kayys.syirkah.accounting.domain.document.UploadSessionId;

public record CompleteUpload(String tenantId, UploadSessionId uploadSessionId) {}
