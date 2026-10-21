package tech.kayys.numou.education.pesantren.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.pesantren.domain.event.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PesantrenDomainTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final PesantrenId PESANTREN = PesantrenId.generate();

    private static Santri santri(Gender g, Residency r) {
        return Santri.register(SantriId.generate(), TENANT, PESANTREN, "S-" + System.nanoTime(), "Ahmad Fauzi", g,
                LocalDate.of(2010, 1, 1), r, "Bapak Fauzi", "0812");
    }

    @Test
    void quranMeta_isConsistentWithTheMushaf() {
        assertThat(QuranMeta.SURAH_COUNT).isEqualTo(114);
        assertThat(QuranMeta.totalAyah()).isEqualTo(6236);
        assertThat(QuranMeta.ayahCount(1)).isEqualTo(7);
        assertThat(QuranMeta.ayahCount(2)).isEqualTo(286);
        assertThat(QuranMeta.ayahCount(114)).isEqualTo(6);
    }

    @Test
    void quranRange_validatesBounds() {
        assertThat(new QuranRange(36, 1, 12).ayahCount()).isEqualTo(12);
        assertThatThrownBy(() -> new QuranRange(1, 1, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuranRange(115, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuranRange(2, 5, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hafalan_onlyFluentNewSetoranCountsAsProgress() {
        var range = new QuranRange(78, 1, 10);
        var fluent = HafalanLog.record(HafalanLogId.generate(), TENANT, SantriId.generate(), LocalDate.now(),
                HafalanType.SETORAN_BARU, range, HafalanResult.LANCAR, TeacherId.generate());
        var repeat = HafalanLog.record(HafalanLogId.generate(), TENANT, SantriId.generate(), LocalDate.now(),
                HafalanType.SETORAN_BARU, range, HafalanResult.ULANG, null);
        var revision = HafalanLog.record(HafalanLogId.generate(), TENANT, SantriId.generate(), LocalDate.now(),
                HafalanType.MURAJAAH, range, HafalanResult.LANCAR, null);
        assertThat(fluent.countsAsNewProgress()).isTrue();
        assertThat(repeat.countsAsNewProgress()).isFalse();
        assertThat(revision.countsAsNewProgress()).isFalse();
        assertThat(fluent.pullDomainEvents()).hasSize(1).first().isInstanceOf(HafalanRecorded.class);
    }

    @Test
    void santri_onlyActiveMukimCanBeHoused() {
        Santri kalong = santri(Gender.LAKI_LAKI, Residency.KALONG);
        assertThatThrownBy(() -> kalong.assignRoom(DormRoomId.generate())).hasMessageContaining("MUKIM");
        Santri mukim = santri(Gender.LAKI_LAKI, Residency.MUKIM);
        mukim.assignRoom(DormRoomId.generate());
        assertThatThrownBy(() -> mukim.assignRoom(DormRoomId.generate())).hasMessageContaining("already has a room");
    }

    @Test
    void santri_leavingClearsRoom_andIsTerminal() {
        Santri s = santri(Gender.PEREMPUAN, Residency.MUKIM);
        s.assignRoom(DormRoomId.generate());
        s.graduate();
        assertThat(s.getStatus()).isEqualTo(SantriStatus.ALUMNI);
        assertThat(s.getRoomId()).isNull();
        assertThatThrownBy(s::withdraw).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void dormitory_enforcesGenderCapacityAndSingleRoom() {
        Dormitory d = Dormitory.create(DormitoryId.generate(), TENANT, PESANTREN, "Asrama Putra", Gender.LAKI_LAKI);
        DormRoomId room = d.addRoom("A1", 1);
        DormRoomId other = d.addRoom("A2", 2);
        SantriId first = SantriId.generate();
        assertThatThrownBy(() -> d.assign(room, SantriId.generate(), Gender.PEREMPUAN)).hasMessageContaining("LAKI_LAKI");
        d.assign(room, first, Gender.LAKI_LAKI);
        assertThatThrownBy(() -> d.assign(room, SantriId.generate(), Gender.LAKI_LAKI)).hasMessageContaining("full");
        assertThatThrownBy(() -> d.assign(other, first, Gender.LAKI_LAKI)).hasMessageContaining("already housed");
        assertThat(d.occupancy()).isEqualTo(1);
        assertThat(d.capacity()).isEqualTo(3);
        d.vacate(first);
        assertThat(d.occupancy()).isEqualTo(0);
    }

    @Test
    void halaqah_capacityAndMembership() {
        Halaqah h = Halaqah.create(HalaqahId.generate(), TENANT, PESANTREN, "Halaqah Fathul Qarib", "Fathul Qarib",
                StudyMethod.BANDONGAN, TeacherId.generate(), DayOfWeek.MONDAY, LocalTime.of(5, 30), 1);
        SantriId s = SantriId.generate();
        h.join(s);
        assertThatThrownBy(() -> h.join(SantriId.generate())).hasMessageContaining("full");
        assertThatThrownBy(() -> h.join(s)).hasMessageContaining("already");
        h.leave(s);
        assertThat(h.getMembers()).isEmpty();
    }

    @Test
    void leavePermit_approveThenReturnLate() {
        LeavePermit p = LeavePermit.request(LeavePermitId.generate(), TENANT, SantriId.generate(),
                LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 8), "Acara keluarga");
        p.approve("Ustadz Hasan");
        assertThat(p.isOverdue(LocalDate.of(2025, 9, 9))).isTrue();
        assertThat(p.markReturned(LocalDate.of(2025, 9, 10))).isEqualTo(2);
        assertThat(p.getStatus()).isEqualTo(LeavePermitStatus.RETURNED);
        assertThat(p.pullDomainEvents()).hasSize(3).first().isInstanceOf(LeavePermitRequested.class);
    }

    @Test
    void leavePermit_onTimeReturnIsZeroLate() {
        LeavePermit p = LeavePermit.request(LeavePermitId.generate(), TENANT, SantriId.generate(),
                LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 8), "Sakit");
        p.approve("Ustadz Hasan");
        assertThat(p.markReturned(LocalDate.of(2025, 9, 8))).isEqualTo(0);
    }

    @Test
    void leavePermit_decisionRules() {
        LeavePermit p = LeavePermit.request(LeavePermitId.generate(), TENANT, SantriId.generate(),
                LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 8), "Acara");
        assertThatThrownBy(() -> p.markReturned(LocalDate.of(2025, 9, 9))).hasMessageContaining("APPROVED");
        p.reject("Ustadz Hasan", "Sedang ujian");
        assertThatThrownBy(() -> p.approve("Ustadz Hasan")).hasMessageContaining("already decided");
        assertThatThrownBy(() -> LeavePermit.request(LeavePermitId.generate(), TENANT, SantriId.generate(),
                LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 5), "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pesantren_nsppMustBeDigits() {
        assertThat(Pesantren.register(PesantrenId.generate(), TENANT, "Pondok Al-Ikhlas", "510032100123", PesantrenType.KOMBINASI, null)
                .pullDomainEvents()).hasSize(1).first().isInstanceOf(PesantrenRegistered.class);
        assertThatThrownBy(() -> Pesantren.register(PesantrenId.generate(), TENANT, "X", "12A", PesantrenType.SALAF, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
