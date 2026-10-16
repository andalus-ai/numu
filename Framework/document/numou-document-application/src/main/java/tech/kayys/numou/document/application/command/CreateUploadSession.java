package tech.kayys.numou.document.application.command;

import tech.kayys.numou.accounting.domain.document.DocumentClassification;
import tech.kayys.numou.accounting.domain.document.DocumentMetadata;
import tech.kayys.numou.accounting.domain.document.DocumentType;

import java.time.Duration;

public record CreateUploadSession(
        String tenantId,
        DocumentType documentType,
        DocumentClassification classification,
        DocumentMetadata metadata,
        String expectedSha256,
        Duration lifetime
) {}
