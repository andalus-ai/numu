package tech.kayys.numou.education.madrasah.application.command;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.madrasah.domain.Prayer;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Set;

public record LogDailyIbadahCommand(TenantId tenantId, StudentId studentId, LocalDate date, Set<Prayer> prayers,
                                    boolean sunnahFast, int tilawahPages) {}
