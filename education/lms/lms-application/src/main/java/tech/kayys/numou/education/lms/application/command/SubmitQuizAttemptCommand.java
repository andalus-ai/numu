package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Map;

/** {@code answers} maps question index to chosen option index. */
public record SubmitQuizAttemptCommand(TenantId tenantId, QuizId quizId, StudentId studentId,
                                       Map<Integer, Integer> answers, Instant now) {}
