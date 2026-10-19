package tech.kayys.numou.asset.interfaces.rest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.asset.application.meter.*;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.meter.*;
import tech.kayys.numou.asset.domain.repository.*;
import tech.kayys.numou.foundation.adapter.context.TenantContext;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
@Path("/api/v1/meters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Meter API", description = "Meter and usage readings endpoints")
public class AssetMeterResource {
  private final RegisterAssetMeterHandler registerHandler;
  private final RecordMeterReadingHandler recordHandler;
  private final GetAssetMetersHandler assetMetersHandler;
  private final GetMeterReadingsHandler readingsHandler;
  private final GetMeterUsageHandler usageHandler;
  @Inject
  public AssetMeterResource(AssetRepository assets, AssetMeterRepository meters, MeterReadingRepository readings, EventPublisher publisher, UnitOfWork uow, DomainClock clock) {
    this.registerHandler = new RegisterAssetMeterHandler(assets, meters);
    this.recordHandler = new RecordMeterReadingHandler(assets, meters, readings, publisher, uow, clock);
    this.assetMetersHandler = new GetAssetMetersHandler(meters, readings);
    this.readingsHandler = new GetMeterReadingsHandler(readings);
    this.usageHandler = new GetMeterUsageHandler(meters, readings);
  }
  private static String tenant(String header) {
    if (header != null && !header.isBlank()) { TenantContext.setTenantId(header); return header; }
    String current = TenantContext.getTenantId();
    if (current == null || current.isBlank()) throw new BusinessRuleViolation("tenant is required (X-Tenant-Id header)");
    return current;
  }
  @POST
  @Operation(summary = "Register a meter")
  public Uni<Response> register(@HeaderParam("X-Tenant-Id") String header, RegisterRequest req) {
    String tenantId = tenant(header);
    return registerHandler.handle(new RegisterAssetMeterCommand(tenantId, req.assetId(), req.type(), req.unit(), req.behavior(), req.name(), req.replacementOf()))
      .map(result -> {
        MeterResult created = result.orElseThrow();
        return Response.status(Response.Status.CREATED).entity(created).build();
      }).onFailure().recoverWithItem(AssetMeterResource::toErrorResponse);
  }
  @POST
  @Path("/{meterId}/readings")
  @Operation(summary = "Record a reading")
  public Uni<Response> record(@HeaderParam("X-Tenant-Id") String header, @PathParam("meterId") UUID meterId, RecordRequest req) {
    String tenantId = tenant(header);
    return recordHandler.handle(new RecordMeterReadingCommand(tenantId, req.assetId(), meterId, req.value(), req.unit(), req.recordedAt(), req.recordedBy(), req.type(), req.source(), req.sourceRef()))
      .map(result -> {
        MeterReadingResult created = result.orElseThrow();
        Response.Status s = created.duplicate() ? Response.Status.OK : Response.Status.CREATED;
        return Response.status(s).entity(created).build();
      }).onFailure().recoverWithItem(AssetMeterResource::toErrorResponse);
  }
  @GET
  @Path("/asset/{assetId}")
  @Operation(summary = "List meters with current reading")
  public Uni<Response> byAsset(@HeaderParam("X-Tenant-Id") String header, @PathParam("assetId") UUID assetId) {
    String tenantId = tenant(header);
    return assetMetersHandler.handle(new GetAssetMetersQuery(tenantId, assetId)).map(list -> Response.ok(list).build()).onFailure().recoverWithItem(AssetMeterResource::toErrorResponse);
  }
  @GET
  @Path("/{meterId}/readings")
  @Operation(summary = "List readings")
  public Uni<Response> readings(@HeaderParam("X-Tenant-Id") String header, @PathParam("meterId") UUID meterId) {
    String tenantId = tenant(header);
    return readingsHandler.handle(new GetMeterReadingsQuery(tenantId, meterId)).map(list -> Response.ok(list).build()).onFailure().recoverWithItem(AssetMeterResource::toErrorResponse);
  }
  @GET
  @Path("/{meterId}/current")
  @Operation(summary = "Current reading")
  public Uni<Response> current(@HeaderParam("X-Tenant-Id") String header, @PathParam("meterId") UUID meterId) {
    String tenantId = tenant(header);
    return readingsHandler.handle(new GetMeterReadingsQuery(tenantId, meterId)).map(list -> {
      if (list.isEmpty()) return Response.status(Response.Status.NOT_FOUND).entity(new ErrorResponse("meter.no-readings", "No readings for meter")).build();
      return Response.ok(list.get(list.size() - 1)).build();
    }).onFailure().recoverWithItem(AssetMeterResource::toErrorResponse);
  }
  @GET
  @Path("/{meterId}/usage")
  @Operation(summary = "Usage in window")
  public Uni<Response> usage(@HeaderParam("X-Tenant-Id") String header, @PathParam("meterId") UUID meterId, @QueryParam("from") String from, @QueryParam("to") String to) {
    String tenantId = tenant(header);
    Instant f = from == null ? null : Instant.parse(from);
    Instant t = to == null ? null : Instant.parse(to);
    return usageHandler.handle(new GetMeterUsageQuery(tenantId, meterId, f, t)).map(u -> Response.ok(u).build()).onFailure().recoverWithItem(AssetMeterResource::toErrorResponse);
  }
  static Response toErrorResponse(Throwable failure) {
    Throwable cause = unwrap(failure);
    if (cause instanceof ApplicationErrorException ae) {
      ApplicationError error = ae.error();
      Response.Status status = switch (error.code()) {
        case "meter.not-found", "asset.not-found" -> Response.Status.NOT_FOUND;
        case "meter.monotonic-violation", "meter.unit-mismatch", "meter.rule-violation" -> Response.Status.CONFLICT;
        default -> Response.Status.BAD_REQUEST;
      };
      if (error.code().contains("monotonic")) status = Response.Status.CONFLICT;
      return Response.status(status).entity(new ErrorResponse(error.code(), error.message())).build();
    }
    if (cause instanceof BusinessRuleViolation rule) return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse("meter.rule-violation", rule.getMessage())).build();
    if (cause instanceof IllegalArgumentException bad) return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse("meter.invalid-argument", bad.getMessage())).build();
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new ErrorResponse("meter.unexpected", cause.getMessage() == null ? "Unexpected error" : cause.getMessage())).build();
  }
  private static Throwable unwrap(Throwable th) {
    Throwable c = th;
    while ((c instanceof CompletionException || c instanceof ExecutionException) && c.getCause() != null) c = c.getCause();
    return c;
  }
  public record RegisterRequest(UUID assetId, MeterType type, MeterUnit unit, MeterBehavior behavior, String name, UUID replacementOf) {}
  public record RecordRequest(UUID assetId, BigDecimal value, MeterUnit unit, Instant recordedAt, String recordedBy, MeterReadingType type, String source, String sourceRef) {}
  public record ErrorResponse(String code, String message) {}
}
