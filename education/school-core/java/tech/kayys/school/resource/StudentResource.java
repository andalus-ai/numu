package tech.kayys.school.resource;


import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.school.model.Semester;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;
import tech.kayys.school.service.StudentService;
import jakarta.validation.Valid;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;



@Path("/api/students")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Student Resource", description = "Operations related to student management")
public class StudentResource {

    @Inject
    StudentService studentService;

    @GET
    @Operation(summary = "Get all students", description = "Returns a list of all students")
    @APIResponse(responseCode = "200", description = "List of students")
    public Response getAllStudents(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("nisn") String nisn,
            @QueryParam("name") String name,
            @QueryParam("grade") String grade) {
        
        List<StudentDTO> students = studentService.findStudents(page, size, nisn, name, grade);
        long total = studentService.countStudents(nisn, name, grade);
        
        return Response.ok(students)
                .header("X-Total-Count", total)
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get student by ID", description = "Returns a student based on ID")
    @APIResponse(responseCode = "200", description = "Student found")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getStudentById(
            @Parameter(description = "Student ID", required = true) @PathParam("id") Long id) {
        
        StudentDTO student = studentService.findById(id);
        return Response.ok(student).build();
    }

    @GET
    @Path("/nisn/{nisn}")
    @Operation(summary = "Get student by NISN", description = "Returns a student based on NISN")
    @APIResponse(responseCode = "200", description = "Student found")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getStudentByNisn(
            @Parameter(description = "Student NISN", required = true) @PathParam("nisn") String nisn) {
        
        StudentDTO student = studentService.findByNisn(nisn);
        return Response.ok(student).build();
    }

    @POST
    @Operation(summary = "Create new student", description = "Creates a new student record")
    @APIResponse(responseCode = "201", description = "Student created successfully")
    @APIResponse(responseCode = "400", description = "Invalid student data")
    public Response createStudent(@Valid StudentDTO studentDTO) {
        StudentDTO created = studentService.createStudent(studentDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update student", description = "Updates an existing student record")
    @APIResponse(responseCode = "200", description = "Student updated successfully")
    @APIResponse(responseCode = "404", description = "Student not found")
    @APIResponse(responseCode = "400", description = "Invalid student data")
    public Response updateStudent(
            @Parameter(description = "Student ID", required = true) @PathParam("id") Long id,
            @Valid StudentDTO studentDTO) {
        
        StudentDTO updated = studentService.updateStudent(id, studentDTO);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete student", description = "Deletes a student record")
    @APIResponse(responseCode = "204", description = "Student deleted successfully")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response deleteStudent(
            @Parameter(description = "Student ID", required = true) @PathParam("id") Long id) {
        
        studentService.deleteStudent(id);
        return Response.noContent().build();
    }
}

