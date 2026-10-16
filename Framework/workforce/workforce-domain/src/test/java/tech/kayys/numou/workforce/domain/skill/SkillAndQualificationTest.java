package tech.kayys.syirkah.workforce.domain.skill;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.workforce.domain.qualification.Qualification;
import tech.kayys.syirkah.workforce.domain.qualification.QualificationId;
import tech.kayys.syirkah.workforce.domain.qualification.WorkerQualification;
import tech.kayys.syirkah.workforce.domain.qualification.WorkerQualificationId;
import tech.kayys.syirkah.workforce.domain.worker.WorkerId;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAndQualificationTest {

    @Test
    void shouldManageSkillLifecycleAndWorkerSkillAssessment() {
        var skill = Skill.create(SkillId.generate(), "JAVA", "Java Programming", "Backend language", null);
        assertThat(skill.isActive()).isTrue();
        assertThat(skill.pullDomainEvents()).hasSize(1);

        var workerSkillId = WorkerSkillId.generate();
        var workerId = WorkerId.generate();
        var ws = WorkerSkill.create(workerSkillId, workerId, skill.getId(), ProficiencyLevel.INTERMEDIATE, LocalDate.of(2026, 1, 15));
        assertThat(ws.getProficiency()).isEqualTo(ProficiencyLevel.INTERMEDIATE);
        assertThat(ws.isActive()).isTrue();

        ws.assess(ProficiencyLevel.ADVANCED, LocalDate.of(2026, 6, 15));
        assertThat(ws.getProficiency()).isEqualTo(ProficiencyLevel.ADVANCED);
        assertThat(ws.getLastAssessedDate()).isEqualTo(LocalDate.of(2026, 6, 15));

        ws.remove();
        assertThat(ws.isActive()).isFalse();
    }

    @Test
    void shouldManageQualificationAndWorkerQualificationValidity() {
        var qualification = Qualification.create(
                QualificationId.generate(),
                "PMP",
                "Project Management Professional",
                "PMI",
                "Certified project manager"
        );
        assertThat(qualification.isActive()).isTrue();

        var workerQual = WorkerQualification.create(
                WorkerQualificationId.generate(),
                WorkerId.generate(),
                qualification.getId(),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                "CERT-12345"
        );

        assertThat(workerQual.isActive()).isTrue();
        assertThat(workerQual.isExpired(LocalDate.of(2026, 6, 1))).isTrue();
        assertThat(workerQual.isExpired(LocalDate.of(2025, 6, 1))).isFalse();

        workerQual.revoke();
        assertThat(workerQual.isActive()).isFalse();
    }
}
