package tech.kayys.numou.education.madrasah.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.madrasah.domain.event.IbadahLogged;
import tech.kayys.numou.education.madrasah.domain.event.MadrasahProfileRegistered;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MadrasahDomainTest {

    private static final TenantId TENANT = TenantId.generate();

    private static MadrasahProfile profile(EducationLevel level) {
        return MadrasahProfile.register(MadrasahProfileId.generate(), TENANT, SchoolId.generate(),
                Nsm.of("121232100001"), level, MadrasahOwnership.NEGERI);
    }

    @Test
    void profile_acceptsMadrasahLevels_andRaisesEvent() {
        MadrasahProfile p = profile(EducationLevel.MTS);
        assertThat(p.pullDomainEvents()).hasSize(1).first().isInstanceOf(MadrasahProfileRegistered.class);
        assertThat(p.getPaiSubjects()).hasSize(5);
    }

    @Test
    void profile_rejectsNonMadrasahLevel() {
        assertThatThrownBy(() -> profile(EducationLevel.SMP)).hasMessageContaining("not a madrasah level");
    }

    @Test
    void nsm_mustBe12Digits() {
        assertThatThrownBy(() -> Nsm.of("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nsm.of("12123210000A")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profile_mustKeepAtLeastOnePaiSubject() {
        MadrasahProfile p = profile(EducationLevel.MI);
        assertThatThrownBy(() -> p.offerPaiSubjects(Set.of())).isInstanceOf(IllegalArgumentException.class);
        p.offerPaiSubjects(Set.of(PaiSubject.FIKIH));
        assertThat(p.getPaiSubjects()).hasSize(1);
    }

    @Test
    void ibadah_completionAndUpdate() {
        DailyIbadahLog l = DailyIbadahLog.log(IbadahLogId.generate(), TENANT, StudentId.generate(), LocalDate.of(2025, 9, 1),
                List.of(Prayer.SUBUH, Prayer.DZUHUR), false, 2);
        assertThat(l.prayerCompletion()).isEqualTo(0.4);
        l.update(List.of(Prayer.values()), true, 5);
        assertThat(l.prayerCompletion()).isEqualTo(1.0);
        assertThat(l.pullDomainEvents()).hasSize(2).first().isInstanceOf(IbadahLogged.class);
    }

    @Test
    void ibadah_rejectsNegativePages() {
        assertThatThrownBy(() -> DailyIbadahLog.log(IbadahLogId.generate(), TENANT, StudentId.generate(), LocalDate.now(),
                List.of(), false, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hijri_ramadanDetectionAndFormat() {
        assertThat(HijriDates.isRamadan(LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(HijriDates.isRamadan(LocalDate.of(2026, 6, 1))).isFalse();
        assertThat(HijriDates.format(LocalDate.of(2026, 3, 1))).isEqualTo(HijriDates.of(LocalDate.of(2026, 3, 1)).get(java.time.temporal.ChronoField.DAY_OF_MONTH) + " Ramadan 1447 H");
    }
}
