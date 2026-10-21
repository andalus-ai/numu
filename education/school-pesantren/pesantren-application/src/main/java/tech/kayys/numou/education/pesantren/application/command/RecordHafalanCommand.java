package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.pesantren.domain.HafalanResult;
import tech.kayys.numou.education.pesantren.domain.HafalanType;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

/** {@code examiner} (the ustadz who heard the recitation) is optional. */
public record RecordHafalanCommand(TenantId tenantId, SantriId santriId, LocalDate date, HafalanType type,
                                   int surah, int fromAyah, int toAyah, HafalanResult result, TeacherId examiner) {}
