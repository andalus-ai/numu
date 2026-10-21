package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

public record CreateAssignmentCommand(TenantId tenantId, CourseId courseId, String title, String instructions,
                                      Instant dueAt, int maxScore, boolean allowLate, boolean publish) {}
