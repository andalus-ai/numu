package tech.kayys.numou.education.pesantren.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.pesantren.adapter.memory.*;
import tech.kayys.numou.education.pesantren.domain.*;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PesantrenHandlersTest {

    private final TenantId tenant = TenantId.generate();
    private final InMemoryPesantrenRepository pesantrens = new InMemoryPesantrenRepository();
    private final InMemorySantriRepository santris = new InMemorySantriRepository();
    private final InMemoryDormitoryRepository dorms = new InMemoryDormitoryRepository();
    private final InMemoryHafalanLogRepository hafalan = new InMemoryHafalanLogRepository();
    private final InMemoryLeavePermitRepository permits = new InMemoryLeavePermitRepository();

    private PesantrenId pesantren() {
        return new RegisterPesantrenHandler(pesantrens).handle(new RegisterPesantrenCommand(tenant, "Pondok Al-Ikhlas",
                null, PesantrenType.SALAF, null)).toCompletableFuture().join().orElseThrow();
    }

    private Result<SantriId> santri(PesantrenId p, String number, Gender g, Residency r) {
        return new RegisterSantriHandler(pesantrens, santris).handle(new RegisterSantriCommand(tenant, p, number, "Ahmad",
                g, LocalDate.of(2010, 1, 1), r, "Bapak Ahmad", "0812")).toCompletableFuture().join();
    }

    private Dormitory dorm(PesantrenId p, Gender g, int cap) {
        Dormitory d = Dormitory.create(DormitoryId.generate(), tenant, p, "Asrama", g);
        d.addRoom("A1", cap);
        dorms.save(d).toCompletableFuture().join();
        return d;
    }

    private Result<DormRoomId> assign(Dormitory d, SantriId s) {
        DormRoomId room = d.getRooms().keySet().iterator().next();
        return new AssignSantriToRoomHandler(dorms, santris).handle(new AssignSantriToRoomCommand(tenant, d.getId(), room, s))
                .toCompletableFuture().join();
    }

    @Test
    void registerSantri_rejectsDuplicateNumber_andUnknownPesantren() {
        PesantrenId p = pesantren();
        assertThat(santri(p, "S-1", Gender.LAKI_LAKI, Residency.MUKIM).isSuccess()).isTrue();
        assertThat(santri(p, "S-1", Gender.LAKI_LAKI, Residency.MUKIM).failureError().get().code()).isEqualTo("pesantren.santri.duplicate-number");
        assertThat(santri(PesantrenId.generate(), "S-2", Gender.LAKI_LAKI, Residency.MUKIM).failureError().get().code()).isEqualTo("pesantren.not-found");
    }

    @Test
    void assign_housesMukimSantri_andUpdatesBothAggregates() {
        PesantrenId p = pesantren();
        Dormitory d = dorm(p, Gender.LAKI_LAKI, 2);
        SantriId s = santri(p, "S-1", Gender.LAKI_LAKI, Residency.MUKIM).orElseThrow();
        assertThat(assign(d, s).isSuccess()).isTrue();
        assertThat(santris.findById(s).toCompletableFuture().join().get().getRoomId()).isNotNull();
        assertThat(dorms.findById(d.getId()).toCompletableFuture().join().get().occupancy()).isEqualTo(1);
    }

    @Test
    void assign_rejectsKalong_wrongGender_andFullRoom_withoutSideEffects() {
        PesantrenId p = pesantren();
        Dormitory d = dorm(p, Gender.LAKI_LAKI, 1);
        SantriId kalong = santri(p, "S-1", Gender.LAKI_LAKI, Residency.KALONG).orElseThrow();
        SantriId girl = santri(p, "S-2", Gender.PEREMPUAN, Residency.MUKIM).orElseThrow();
        SantriId boy1 = santri(p, "S-3", Gender.LAKI_LAKI, Residency.MUKIM).orElseThrow();
        SantriId boy2 = santri(p, "S-4", Gender.LAKI_LAKI, Residency.MUKIM).orElseThrow();

        assertThat(assign(d, kalong).failureError().get().code()).isEqualTo("pesantren.santri.not-mukim");
        assertThat(assign(d, girl).failureError().get().code()).isEqualTo("pesantren.dormitory.cannot-assign");
        assertThat(santris.findById(girl).toCompletableFuture().join().get().getRoomId()).isNull();

        assertThat(assign(d, boy1).isSuccess()).isTrue();
        assertThat(assign(d, boy2).failureError().get().code()).isEqualTo("pesantren.dormitory.cannot-assign");
        assertThat(santris.findById(boy2).toCompletableFuture().join().get().getRoomId()).isNull();
        assertThat(assign(d, boy1).failureError().get().code()).isEqualTo("pesantren.santri.already-housed");
    }

    @Test
    void hafalan_recordsValidRange_andRejectsInvalid() {
        PesantrenId p = pesantren();
        SantriId s = santri(p, "S-1", Gender.LAKI_LAKI, Residency.MUKIM).orElseThrow();
        var handler = new RecordHafalanHandler(santris, hafalan);
        var ok = handler.handle(new RecordHafalanCommand(tenant, s, LocalDate.now(), HafalanType.SETORAN_BARU, 36, 1, 12, HafalanResult.LANCAR, null))
                .toCompletableFuture().join();
        assertThat(ok.isSuccess()).isTrue();
        var bad = handler.handle(new RecordHafalanCommand(tenant, s, LocalDate.now(), HafalanType.SETORAN_BARU, 1, 1, 9, HafalanResult.LANCAR, null))
                .toCompletableFuture().join();
        assertThat(bad.failureError().get().code()).isEqualTo("pesantren.hafalan.invalid");
    }

    @Test
    void hafalan_rejectsAlumni() {
        PesantrenId p = pesantren();
        SantriId s = santri(p, "S-1", Gender.LAKI_LAKI, Residency.MUKIM).orElseThrow();
        Santri alumnus = santris.findById(s).toCompletableFuture().join().get();
        alumnus.graduate();
        santris.save(alumnus).toCompletableFuture().join();
        var r = new RecordHafalanHandler(santris, hafalan).handle(new RecordHafalanCommand(tenant, s, LocalDate.now(),
                HafalanType.MURAJAAH, 1, 1, 7, HafalanResult.LANCAR, null)).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("pesantren.santri.inactive");
    }

    @Test
    void leavePermit_onlyForMukim_andValidatesDates() {
        PesantrenId p = pesantren();
        SantriId mukim = santri(p, "S-1", Gender.LAKI_LAKI, Residency.MUKIM).orElseThrow();
        SantriId kalong = santri(p, "S-2", Gender.LAKI_LAKI, Residency.KALONG).orElseThrow();
        var handler = new RequestLeavePermitHandler(santris, permits);
        LocalDate from = LocalDate.of(2025, 9, 5);

        assertThat(handler.handle(new RequestLeavePermitCommand(tenant, mukim, from, from.plusDays(2), "Acara keluarga"))
                .toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(handler.handle(new RequestLeavePermitCommand(tenant, kalong, from, from.plusDays(2), "x"))
                .toCompletableFuture().join().failureError().get().code()).isEqualTo("pesantren.leave.not-mukim");
        assertThat(handler.handle(new RequestLeavePermitCommand(tenant, mukim, from, from.minusDays(1), "x"))
                .toCompletableFuture().join().failureError().get().code()).isEqualTo("pesantren.leave.invalid");
    }
}
