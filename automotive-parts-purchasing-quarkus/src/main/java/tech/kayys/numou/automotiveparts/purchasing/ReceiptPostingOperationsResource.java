package tech.kayys.numou.automotiveparts.purchasing;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Authenticated HTTP adapter for tenant-scoped receipt-posting operations. */
@Path("/api/v1/automotive-parts/receipt-postings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ReceiptPostingOperationsResource {
    private final IdentityReceiptPostingOperations operations;

    @Inject
    public ReceiptPostingOperationsResource(IdentityReceiptPostingOperations operations) {
        this.operations = operations;
    }

    @GET
    public Uni<ReceiptPostingOperationsSummary> summary() {
        return mapFailures(operations.summary());
    }

    @GET
    @Path("/dead-letters")
    public Uni<DeadLetterReceiptPostingPage> deadLetters(
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return mapFailures(operations.listDeadLetters(cursor, limit));
    }

    @GET
    @Path("/dead-letters/{receiptId}")
    public Uni<DeadLetterReceiptPostingDetail> detail(
            @PathParam("receiptId") String receiptId,
            @QueryParam("historyLimit") @DefaultValue("100") int historyLimit) {
        return mapFailures(operations.getDeadLetter(receiptId, historyLimit)
                .onItem().transform(value -> value.orElseThrow(ReceiptPostingNotFoundException::new)));
    }

    @POST
    @Path("/dead-letters/{receiptId}/requeue")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> requeue(@PathParam("receiptId") String receiptId, RequeueRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new WebApplicationException("A non-empty requeue reason is required", 400);
        }
        return mapFailures(operations.requeue(receiptId, request.reason()))
                .replaceWith(Response.noContent().build());
    }

    private static <T> Uni<T> mapFailures(Uni<T> operation) {
        return operation.onFailure().transform(ReceiptPostingOperationsResource::httpFailure);
    }

    private static RuntimeException httpFailure(Throwable failure) {
        if (failure instanceof WebApplicationException web) return web;
        if (failure instanceof SecurityException) return new WebApplicationException("Forbidden", 403);
        if (failure instanceof ReceiptPostingNotFoundException) {
            return new WebApplicationException("Receipt posting not found", 404);
        }
        if (failure instanceof IllegalArgumentException) {
            return new WebApplicationException("Invalid receipt-posting request", 400);
        }
        return new WebApplicationException("Receipt-posting operation failed", 500);
    }

    public record RequeueRequest(String reason) { }
}
