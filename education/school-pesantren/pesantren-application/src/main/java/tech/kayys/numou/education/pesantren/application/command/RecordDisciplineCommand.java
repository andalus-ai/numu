package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.DisciplineCategory;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

public record RecordDisciplineCommand(TenantId tenantId, SantriId santriId, LocalDate date, DisciplineCategory category,
                                      String description, int points, String recordedBy) {}
