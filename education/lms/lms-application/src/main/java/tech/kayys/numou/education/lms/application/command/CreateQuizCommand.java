package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.education.lms.domain.QuizQuestion;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;

public record CreateQuizCommand(TenantId tenantId, CourseId courseId, String title, int maxAttempts,
                                List<QuizQuestion> questions, boolean publish) {}
