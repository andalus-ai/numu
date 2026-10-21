package tech.kayys.school.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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

@Path("/api/school")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "School Resource", description = "Operations related to school information")
public class SchoolResource {

    @Inject
    SchoolService schoolService;

    @GET
    @Operation(summary = "Get school information", description = "Returns information about the school")
    @APIResponse(responseCode = "200", description = "School information")
    public Response getSchoolInfo() {
        SchoolDTO school = schoolService.getSchoolInfo();
        return Response.ok(school).build();
    }

    @PUT
    @Operation(summary = "Update school information", description = "Updates school information")
    @APIResponse(responseCode = "200", description = "School information updated successfully")
    @APIResponse(responseCode = "400", description = "Invalid school data")
    public Response updateSchoolInfo(@Valid SchoolDTO schoolDTO) {
        SchoolDTO updated = schoolService.updateSchoolInfo(schoolDTO);
        return Response.ok(updated).build();
    }

    @GET
    @Path("/academic-years")
    @Operation(summary = "Get academic years", description = "Returns list of academic years")
    @APIResponse(responseCode = "200", description = "List of academic years")
    public Response getAcademicYears() {
        Object academicYears = schoolService.getAcademicYears();
        return Response.ok(academicYears).build();
    }

    @POST
    @Path("/academic-years")
    @Operation(summary = "Add academic year", description = "Adds a new academic year")
    @APIResponse(responseCode = "201", description = "Academic year added successfully")
    @APIResponse(responseCode = "400", description = "Invalid academic year data")
    public Response addAcademicYear(String academicYear) {
        Object added = schoolService.addAcademicYear(academicYear);
        return Response.status(Response.Status.CREATED).entity(added).build();
    }

    @GET
    @Path("/statistics")
    @Operation(summary = "Get school statistics", description = "Returns various statistics about the school")
    @APIResponse(responseCode = "200", description = "School statistics")
    public Response getSchoolStatistics(
            @QueryParam("academicYear") String academicYear) {
        
        Object statistics = schoolService.getSchoolStatistics(academicYear);
        return Response.ok(statistics).build();
    }
}
