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

@Path("/api/schedules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Schedule Resource", description = "Operations related to class schedules")
public class ScheduleResource {

    @Inject
    ScheduleService scheduleService;

    @GET
    @Path("/class/{classId}")
    @Operation(summary = "Get schedule by class", description = "Returns schedule for a specific class")
    @APIResponse(responseCode = "200", description = "Class schedule")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response getScheduleByClass(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @QueryParam("day") String day) {
        
        List<ScheduleDTO> schedule = scheduleService.findScheduleByClass(classId, day);
        return Response.ok(schedule).build();
    }

    @GET
    @Path("/teacher/{teacherId}")
    @Operation(summary = "Get schedule by teacher", description = "Returns schedule for a specific teacher")
    @APIResponse(responseCode = "200", description = "Teacher schedule")
    @APIResponse(responseCode = "404", description = "Teacher not found")
    public Response getScheduleByTeacher(
            @Parameter(description = "Teacher ID", required = true) @PathParam("teacherId") Long teacherId,
            @QueryParam("day") String day) {
        
        List<ScheduleDTO> schedule = scheduleService.findScheduleByTeacher(teacherId, day);
        return Response.ok(schedule).build();
    }

    @GET
    @Path("/room/{roomId}")
    @Operation(summary = "Get schedule by room", description = "Returns schedule for a specific room")
    @APIResponse(responseCode = "200", description = "Room schedule")
    @APIResponse(responseCode = "404", description = "Room not found")
    public Response getScheduleByRoom(
            @Parameter(description = "Room ID", required = true) @PathParam("roomId") Long roomId,
            @QueryParam("day") String day) {
        
        List<ScheduleDTO> schedule = scheduleService.findScheduleByRoom(roomId, day);
        return Response.ok(schedule).build();
    }

    @POST
    @Operation(summary = "Create new schedule", description = "Creates a new schedule entry")
    @APIResponse(responseCode = "201", description = "Schedule created successfully")
    @APIResponse(responseCode = "400", description = "Invalid schedule data")
    @APIResponse(responseCode = "409", description = "Schedule conflict")
    public Response createSchedule(@Valid ScheduleDTO scheduleDTO) {
        ScheduleDTO created = scheduleService.createSchedule(scheduleDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update schedule", description = "Updates an existing schedule entry")
    @APIResponse(responseCode = "200", description = "Schedule updated successfully")
    @APIResponse(responseCode = "404", description = "Schedule not found")
    @APIResponse(responseCode = "400", description = "Invalid schedule data")
    @APIResponse(responseCode = "409", description = "Schedule conflict")
    public Response updateSchedule(
            @Parameter(description = "Schedule ID", required = true) @PathParam("id") Long id,
            @Valid ScheduleDTO scheduleDTO) {
        
        ScheduleDTO updated = scheduleService.updateSchedule(id, scheduleDTO);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete schedule", description = "Deletes a schedule entry")
    @APIResponse(responseCode = "204", description = "Schedule deleted successfully")
    @APIResponse(responseCode = "404", description = "Schedule not found")
    public Response deleteSchedule(
            @Parameter(description = "Schedule ID", required = true) @PathParam("id") Long id) {
        
        scheduleService.deleteSchedule(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/conflicts")
    @Operation(summary = "Check schedule conflicts", description = "Checks for scheduling conflicts")
    @APIResponse(responseCode = "200", description = "List of conflicts")
    public Response checkScheduleConflicts(
            @QueryParam("academicYear") String academicYear,
            @QueryParam("semester") String semester) {
        
        Object conflicts = scheduleService.checkScheduleConflicts(academicYear, semester);
        return Response.ok(conflicts).build();
    }
}
