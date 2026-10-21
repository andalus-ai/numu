package tech.kayys.school.resource;


import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


import java.util.List;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/attendance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Attendance Resource", description = "Operations related to student attendance")
public class AttendanceResource {

    @Inject
    AttendanceService attendanceService;

    @GET
    @Path("/class/{classId}")
    @Operation(summary = "Get attendance by class", description = "Returns attendance records for a specific class")
    @APIResponse(responseCode = "200", description = "List of attendance records")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response getAttendanceByClass(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @QueryParam("date") String date) {
        
        LocalDate attendanceDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        List<AttendanceDTO> attendance = attendanceService.findAttendanceByClass(classId, attendanceDate);
        return Response.ok(attendance).build();
    }

    @GET
    @Path("/student/{studentId}")
    @Operation(summary = "Get attendance by student", description = "Returns attendance records for a specific student")
    @APIResponse(responseCode = "200", description = "List of attendance records")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getAttendanceByStudent(
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        List<AttendanceDTO> attendance = attendanceService.findAttendanceByStudent(studentId, start, end);
        return Response.ok(attendance).build();
    }

    @POST
    @Path("/class/{classId}")
    @Operation(summary = "Record daily attendance", description = "Records attendance for all students in a class")
    @APIResponse(responseCode = "200", description = "Attendance recorded successfully")
    @APIResponse(responseCode = "400", description = "Invalid attendance data")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response recordDailyAttendance(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @Valid DailyAttendanceDTO dailyAttendanceDTO) {
        
        List<AttendanceDTO> recorded = attendanceService.recordDailyAttendance(classId, dailyAttendanceDTO);
        return Response.ok(recorded).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update attendance", description = "Updates an existing attendance record")
    @APIResponse(responseCode = "200", description = "Attendance updated successfully")
    @APIResponse(responseCode = "404", description = "Attendance record not found")
    @APIResponse(responseCode = "400", description = "Invalid attendance data")
    public Response updateAttendance(
            @Parameter(description = "Attendance ID", required = true) @PathParam("id") Long id,
            @Valid AttendanceDTO attendanceDTO) {
        
        AttendanceDTO updated = attendanceService.updateAttendance(id, attendanceDTO);
        return Response.ok(updated).build();
    }

    @GET
    @Path("/report/student/{studentId}")
    @Operation(summary = "Get attendance report", description = "Returns attendance report for a specific student")
    @APIResponse(responseCode = "200", description = "Attendance report")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getAttendanceReport(
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId,
            @QueryParam("academicYear") String academicYear,
            @QueryParam("semester") String semester) {
        
        Object report = attendanceService.generateAttendanceReport(studentId, academicYear, semester);
        return Response.ok(report).build();
    }
}