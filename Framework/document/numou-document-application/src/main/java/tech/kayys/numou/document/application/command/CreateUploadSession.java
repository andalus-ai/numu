package tech.kayys.syirkah.document.application.command;

import tech.kayys.syirkah.accounting.domain.document.DocumentClassification;
import tech.kayys.syirkah.accounting.domain.document.DocumentMetadata;
import tech.kayys.syirkah.accounting.domain.document.DocumentType;

import java.time.Duration;

public record CreateUploadSession(
        String tenantId,
        DocumentType documentType,
        DocumentClassification classification,
        DocumentMetadata metadata,
        String expectedSha256,
        Duration lifetime
) {}
