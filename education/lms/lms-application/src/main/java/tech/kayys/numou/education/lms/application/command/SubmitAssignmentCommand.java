package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

/** {@code now} is supplied by the caller so the use case stays deterministic and testable. */
public record SubmitAssignmentCommand(TenantId tenantId, AssignmentId assignmentId, StudentId studentId,
                                      String content, Instant now) {}
