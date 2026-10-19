package tech.kayys.numou.document.application.command;

import tech.kayys.numou.accounting.domain.document.UploadSessionId;

public record CompleteUpload(String tenantId, UploadSessionId uploadSessionId) {}
