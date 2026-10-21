package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.education.school.domain.SubjectResult;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;

public record RecordReportCardCommand(TenantId tenantId, ReportCardId reportCardId, List<SubjectResult> subjects,
                                      int sick, int permitted, int absent, String homeroomNote) {}
