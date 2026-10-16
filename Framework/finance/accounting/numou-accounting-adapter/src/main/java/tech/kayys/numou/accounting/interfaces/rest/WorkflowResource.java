package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.workflow.WorkflowEngine;
import tech.kayys.syirkah.accounting.domain.workflow.ProcessContext;
import tech.kayys.syirkah.accounting.domain.workflow.ProcessDefinitionId;
import tech.kayys.syirkah.accounting.domain.workflow.ProcessInstance;
import tech.kayys.syirkah.accounting.domain.workflow.ProcessInstanceId;
import tech.kayys.syirkah.accounting.domain.workflow.TaskId;

import java.util.Map;
import java.util.Optional;

@Path("/api/v1/accounting/workflows")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Workflow API", description = "Financial workflow processes and task execution")
public class WorkflowResource {

    @Inject
    WorkflowEngine workflowEngine;

    
public record StartProcessRequest(String processDefinitionId, Integer version, Map<String, Object> variables) {}
    public record CompleteTaskRequest(String userId, Map<String, Object> taskOutput) {}

    @POST
    @Path("/start")
    @Operation(summary = "Start a new workflow process instance")
    public Uni<Response> startProcess(StartProcessRequest request) {
        return Uni.createFrom().item(() -> {
            ProcessContext ctx = new ProcessContext(request.variables() != null ? request.variables() : Map.of());
            int ver = (request.version() != null && request.version() > 0) ? request.version() : 1;
            ProcessInstance instance = workflowEngine.startProcess(new ProcessDefinitionId(request.processDefinitionId(), ver), ctx);
            return Response.status(Response.Status.CREATED).entity(instance).build();
        });
    }

    @POST
    @Path("/tasks/{taskId}/complete")
    @Operation(summary = "Complete an approval or user task in a workflow")
    public Uni<Response> completeTask(@PathParam("taskId") String taskId, CompleteTaskRequest request) {
        return Uni.createFrom().item(() -> {
            workflowEngine.completeTask(new TaskId(taskId), request.userId(), request.taskOutput());
            return Response.ok(Map.of("status", "COMPLETED", "taskId", taskId)).build();
        });
    }

    @GET
    @Path("/instances/{instanceId}")
    @Operation(summary = "Get workflow process instance status")
    public Uni<Response> getInstance(@PathParam("instanceId") String instanceId) {
        return Uni.createFrom().item(() -> {
            return Optional.ofNullable(workflowEngine.getInstance(new ProcessInstanceId(instanceId)))
                    .map(inst -> Response.ok(inst).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        });
    }
}
