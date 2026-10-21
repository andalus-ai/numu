package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.pesantren.domain.PesantrenId;
import tech.kayys.numou.education.pesantren.domain.Residency;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

public record RegisterSantriCommand(TenantId tenantId, PesantrenId pesantrenId, String santriNumber, String fullName,
                                    Gender gender, LocalDate birthDate, Residency residency,
                                    String guardianName, String guardianPhone) {}
