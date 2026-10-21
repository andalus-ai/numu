package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.pesantren.domain.PesantrenId;
import tech.kayys.numou.education.pesantren.domain.StudyMethod;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record CreateHalaqahCommand(TenantId tenantId, PesantrenId pesantrenId, String name, String kitabTitle,
                                   StudyMethod method, TeacherId ustadz, DayOfWeek day, LocalTime startTime, int capacity) {}
