package tech.kayys.numou.education.pesantren.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.pesantren.adapter.memory.*;
import tech.kayys.numou.education.pesantren.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PesantrenPhase2HandlersTest {

    private static final LocalDate DAY = LocalDate.of(2025, 9, 5);

    private final TenantId tenant = TenantId.generate();
    private final InMemoryPesantrenRepository pesantrens = new InMemoryPesantrenRepository();
    private final InMemorySantriRepository santris = new InMemorySantriRepository();
    private final InMemoryLeavePermitRepository permits = new InMemoryLeavePermitRepository();
    private final InMemoryHalaqahRepository halaqahs = new InMemoryHalaqahRepository();
    private final InMemorySantriWalletRepository wallets = new InMemorySantriWalletRepository();
    private final InMemoryDisciplineRecordRepository discipline = new InMemoryDisciplineRecordRepository();

    private PesantrenId pesantren() {
        return new RegisterPesantrenHandler(pesantrens).handle(new RegisterPesantrenCommand(tenant, "Pondok", null, PesantrenType.SALAF, null))
                .toCompletableFuture().join().orElseThrow();
    }

    private SantriId santri(PesantrenId p, String n) {
        return new RegisterSantriHandler(pesantrens, santris).handle(new RegisterSantriCommand(tenant, p, n, "Ahmad", Gender.LAKI_LAKI,
                LocalDate.of(2010, 1, 1), Residency.MUKIM, "Bapak", "0812")).toCompletableFuture().join().orElseThrow();
    }

    @Test
    void leave_requestApproveReturnLate() {
        SantriId s = santri(pesantren(), "S-1");
        LeavePermitId id = new RequestLeavePermitHandler(santris, permits).handle(new RequestLeavePermitCommand(tenant, s, DAY, DAY.plusDays(2), "Acara"))
                .toCompletableFuture().join().orElseThrow();
        var decide = new DecideLeavePermitHandler(permits);
        var ret = new ReturnFromLeaveHandler(permits);

        assertThat(ret.handle(new ReturnFromLeaveCommand(tenant, id, DAY.plusDays(2))).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("pesantren.leave.cannot-return");
        assertThat(decide.handle(new DecideLeavePermitCommand(tenant, id, true, "Ustadz Hasan", null)).toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(decide.handle(new DecideLeavePermitCommand(tenant, id, false, "Ustadz Hasan", "x")).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("pesantren.leave.cannot-decide");
        assertThat(ret.handle(new ReturnFromLeaveCommand(tenant, id, DAY.plusDays(5))).toCompletableFuture().join().orElseThrow()).isEqualTo(3);
    }

    @Test
    void leave_rejectionAndUnknownPermit() {
        SantriId s = santri(pesantren(), "S-1");
        LeavePermitId id = new RequestLeavePermitHandler(santris, permits).handle(new RequestLeavePermitCommand(tenant, s, DAY, DAY.plusDays(1), "x"))
                .toCompletableFuture().join().orElseThrow();
        assertThat(new DecideLeavePermitHandler(permits).handle(new DecideLeavePermitCommand(tenant, id, false, "Ustadz", "Ujian"))
                .toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(permits.findById(id).toCompletableFuture().join().get().getStatus()).isEqualTo(LeavePermitStatus.REJECTED);
        assertThat(new DecideLeavePermitHandler(permits).handle(new DecideLeavePermitCommand(tenant, LeavePermitId.generate(), true, "U", null))
                .toCompletableFuture().join().failureError().get().code()).isEqualTo("pesantren.leave.not-found");
    }

    @Test
    void halaqah_createAndJoinWithCapacity() {
        PesantrenId p = pesantren();
        HalaqahId h = new CreateHalaqahHandler(pesantrens, halaqahs).handle(new CreateHalaqahCommand(tenant, p, "Halaqah Tafsir", "Jalalain",
                StudyMethod.BANDONGAN, TeacherId.generate(), DayOfWeek.FRIDAY, LocalTime.of(6, 0), 1)).toCompletableFuture().join().orElseThrow();
        var join = new JoinHalaqahHandler(halaqahs, santris);
        SantriId a = santri(p, "S-1");
        SantriId b = santri(p, "S-2");
        assertThat(join.handle(new JoinHalaqahCommand(tenant, h, a)).toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(join.handle(new JoinHalaqahCommand(tenant, h, b)).toCompletableFuture().join().failureError().get().code()).isEqualTo("pesantren.halaqah.cannot-join");
        assertThat(join.handle(new JoinHalaqahCommand(tenant, h, a)).toCompletableFuture().join().isFailure()).isTrue();
    }

    @Test
    void halaqah_rejectsSantriFromOtherPesantren() {
        PesantrenId p1 = pesantren();
        PesantrenId p2 = pesantren();
        HalaqahId h = new CreateHalaqahHandler(pesantrens, halaqahs).handle(new CreateHalaqahCommand(tenant, p1, "H", "K",
                StudyMethod.SOROGAN, TeacherId.generate(), DayOfWeek.MONDAY, LocalTime.of(6, 0), 5)).toCompletableFuture().join().orElseThrow();
        var r = new JoinHalaqahHandler(halaqahs, santris).handle(new JoinHalaqahCommand(tenant, h, santri(p2, "S-9"))).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("pesantren.santri.other-pesantren");
    }

    @Test
    void wallet_openedOnFirstDeposit_thenReused() {
        SantriId s = santri(pesantren(), "S-1");
        var deposit = new DepositToWalletHandler(santris, wallets);
        SantriWalletId first = deposit.handle(new DepositToWalletCommand(tenant, s, new BigDecimal("50000"), DAY, "Kiriman", new BigDecimal("20000")))
                .toCompletableFuture().join().orElseThrow();
        SantriWalletId second = deposit.handle(new DepositToWalletCommand(tenant, s, new BigDecimal("25000"), DAY, "Tambahan", null))
                .toCompletableFuture().join().orElseThrow();
        assertThat(second).isEqualTo(first);
        assertThat(wallets.findById(first).toCompletableFuture().join().get().getBalance().toPlainString()).isEqualTo("75000.00");
    }

    @Test
    void wallet_spendRulesSurfaceAsFailures() {
        SantriId s = santri(pesantren(), "S-1");
        var spend = new SpendFromWalletHandler(wallets);
        assertThat(spend.handle(new SpendFromWalletCommand(tenant, s, new BigDecimal("1000"), DAY, "x")).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("pesantren.wallet.not-found");
        new DepositToWalletHandler(santris, wallets).handle(new DepositToWalletCommand(tenant, s, new BigDecimal("30000"), DAY, null, new BigDecimal("10000")))
                .toCompletableFuture().join().orElseThrow();
        assertThat(spend.handle(new SpendFromWalletCommand(tenant, s, new BigDecimal("8000"), DAY, "kantin")).toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(spend.handle(new SpendFromWalletCommand(tenant, s, new BigDecimal("3000"), DAY, "kantin")).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("pesantren.wallet.cannot-spend");
    }

    @Test
    void wallet_depositRejectedForInactiveSantri() {
        SantriId s = santri(pesantren(), "S-1");
        Santri alumnus = santris.findById(s).toCompletableFuture().join().get();
        alumnus.graduate();
        santris.save(alumnus).toCompletableFuture().join();
        var r = new DepositToWalletHandler(santris, wallets).handle(new DepositToWalletCommand(tenant, s, new BigDecimal("1000"), DAY, null, null))
                .toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("pesantren.santri.inactive");
    }

    @Test
    void discipline_recordedAndValidated() {
        SantriId s = santri(pesantren(), "S-1");
        var handler = new RecordDisciplineHandler(santris, discipline);
        assertThat(handler.handle(new RecordDisciplineCommand(tenant, s, DAY, DisciplineCategory.RINGAN, "Terlambat", 5, "Ustadz Hasan"))
                .toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(discipline.findBySantri(s).toCompletableFuture().join()).hasSize(1);
        assertThat(handler.handle(new RecordDisciplineCommand(tenant, s, DAY, DisciplineCategory.RINGAN, "", 5, "U"))
                .toCompletableFuture().join().failureError().get().code()).isEqualTo("pesantren.discipline.invalid");
    }
}
