package tech.kayys.numou.education.madrasah.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.madrasah.adapter.memory.InMemoryDailyIbadahLogRepository;
import tech.kayys.numou.education.madrasah.adapter.memory.InMemoryMadrasahProfileRepository;
import tech.kayys.numou.education.madrasah.domain.MadrasahOwnership;
import tech.kayys.numou.education.madrasah.domain.Prayer;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MadrasahHandlersTest {

    private final TenantId tenant = TenantId.generate();
    private final InMemoryMadrasahProfileRepository profiles = new InMemoryMadrasahProfileRepository();
    private final InMemoryDailyIbadahLogRepository logs = new InMemoryDailyIbadahLogRepository();

    @Test
    void register_onePerSchool() {
        var handler = new RegisterMadrasahProfileHandler(profiles);
        SchoolId school = SchoolId.generate();
        var first = handler.handle(new RegisterMadrasahProfileCommand(tenant, school, "121232100001", EducationLevel.MA, MadrasahOwnership.SWASTA, true))
                .toCompletableFuture().join();
        assertThat(first.isSuccess()).isTrue();
        assertThat(profiles.findById(first.orElseThrow()).toCompletableFuture().join().get().hasTahfidzProgram()).isTrue();
        var dup = handler.handle(new RegisterMadrasahProfileCommand(tenant, school, "121232100002", EducationLevel.MA, MadrasahOwnership.SWASTA, false))
                .toCompletableFuture().join();
        assertThat(dup.failureError().get().code()).isEqualTo("madrasah.profile.exists");
    }

    @Test
    void register_rejectsGeneralSchoolLevel() {
        var r = new RegisterMadrasahProfileHandler(profiles).handle(new RegisterMadrasahProfileCommand(tenant, SchoolId.generate(),
                "121232100001", EducationLevel.SD, MadrasahOwnership.NEGERI, false)).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("madrasah.profile.invalid");
    }

    @Test
    void logIbadah_sameDayUpdatesInPlace() {
        var handler = new LogDailyIbadahHandler(logs);
        StudentId s = StudentId.generate();
        LocalDate day = LocalDate.of(2025, 9, 2);
        var a = handler.handle(new LogDailyIbadahCommand(tenant, s, day, Set.of(Prayer.SUBUH), false, 1)).toCompletableFuture().join().orElseThrow();
        var b = handler.handle(new LogDailyIbadahCommand(tenant, s, day, Set.of(Prayer.values()), true, 3)).toCompletableFuture().join().orElseThrow();
        assertThat(b).isEqualTo(a);
        assertThat(logs.findById(a).toCompletableFuture().join().get().prayerCompletion()).isEqualTo(1.0);
    }

    @Test
    void logIbadah_rejectsNegativePages() {
        var r = new LogDailyIbadahHandler(logs).handle(new LogDailyIbadahCommand(tenant, StudentId.generate(), LocalDate.now(),
                Set.of(), false, -3)).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("madrasah.ibadah.invalid");
    }
}
