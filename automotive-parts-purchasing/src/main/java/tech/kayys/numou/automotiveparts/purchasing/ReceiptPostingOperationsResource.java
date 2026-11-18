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

import java.util.Optional;
import java.util.function.Supplier;

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
        return invoke(operations::summary);
    }

    @GET
    @Path("/dead-letters")
    public Uni<DeadLetterReceiptPostingPage> deadLetters(
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return invoke(() -> operations.listDeadLetters(cursor, limit));
    }

    @GET
    @Path("/dead-letters/{receiptId}")
    public Uni<DeadLetterReceiptPostingDetail> detail(
            @PathParam("receiptId") String receiptId,
            @QueryParam("historyLimit") @DefaultValue("100") int historyLimit) {
        return invoke(() -> operations.getDeadLetter(receiptId, historyLimit)
                .onItem().transform(optional -> optional.orElseThrow(ReceiptPostingNotFoundException::new)));
    }

    @POST
    @Path("/dead-letters/{receiptId}/requeue")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> requeue(@PathParam("receiptId") String receiptId, RequeueRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new WebApplicationException("A non-empty requeue reason is required", 400);
        }
        return invoke(() -> operations.requeue(receiptId, request.reason()))
                .replaceWith(Response.noContent().build());
    }

    private static <T> Uni<T> invoke(Supplier<Uni<T>> operation) {
        // deferred also maps synchronous validation/context failures thrown before a Uni is returned.
        return Uni.createFrom().deferred(operation)
                .onFailure().transform(ReceiptPostingOperationsResource::httpFailure);
    }

    private static RuntimeException httpFailure(Throwable failure) {
        if (failure instanceof WebApplicationException web) {
            return web;
        }
        if (failure instanceof SecurityException) {
            return new WebApplicationException("Forbidden", 403);
        }
        if (failure instanceof ReceiptPostingNotFoundException) {
            return new WebApplicationException("Receipt posting not found", 404);
        }
        if (failure instanceof IllegalArgumentException) {
            return new WebApplicationException("Invalid receipt-posting request", 400);
        }
        return new WebApplicationException("Receipt-posting operation failed", 500);
    }

    public record RequeueRequest(String reason) {
    }
}
