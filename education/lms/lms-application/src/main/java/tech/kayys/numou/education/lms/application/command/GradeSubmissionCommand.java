package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.SubmissionId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;

public record GradeSubmissionCommand(TenantId tenantId, SubmissionId submissionId, BigDecimal score, String feedback) {}
