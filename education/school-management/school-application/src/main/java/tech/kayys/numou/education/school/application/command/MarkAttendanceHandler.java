package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.AttendanceSheet;
import tech.kayys.numou.education.school.domain.AttendanceSheetId;
import tech.kayys.numou.education.school.spi.AttendanceSheetRepository;
import tech.kayys.numou.education.school.spi.ClassroomRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Opens (or reuses) the day's attendance sheet for a classroom and records marks for rostered students only. */
public class MarkAttendanceHandler {

    private final ClassroomRepository classrooms;
    private final AttendanceSheetRepository sheets;

    public MarkAttendanceHandler(ClassroomRepository classrooms, AttendanceSheetRepository sheets) {
        this.classrooms = Objects.requireNonNull(classrooms);
        this.sheets = Objects.requireNonNull(sheets);
    }

    public CompletionStage<Result<AttendanceSheetId>> handle(MarkAttendanceCommand cmd) {
        return classrooms.findById(cmd.classroomId()).thenCompose(classroomOpt -> {
            if (classroomOpt.isEmpty() || !classroomOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<AttendanceSheetId>failed("school.classroom.not-found", "Classroom not found");
            }
            var classroom = classroomOpt.get();
            for (var studentId : cmd.marks().keySet()) {
                if (!classroom.hasStudent(studentId)) {
                    return Outcomes.<AttendanceSheetId>failed("school.attendance.not-rostered",
                            "Student " + studentId.value() + " is not in this classroom");
                }
            }
            return sheets.findByClassroomAndDate(cmd.classroomId(), cmd.date()).thenCompose(existing -> {
                AttendanceSheet sheet = existing.orElseGet(() ->
                        AttendanceSheet.open(AttendanceSheetId.generate(), cmd.tenantId(), cmd.classroomId(), cmd.date()));
                try {
                    cmd.marks().forEach(sheet::mark);
                    if (cmd.lockAfter()) sheet.lock();
                } catch (IllegalStateException e) {
                    return CompletableFuture.completedFuture(Outcomes.<AttendanceSheetId>fail("school.attendance.locked", e.getMessage()));
                }
                return sheets.save(sheet).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
