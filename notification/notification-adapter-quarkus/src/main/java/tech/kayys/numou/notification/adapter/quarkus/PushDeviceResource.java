package tech.kayys.numou.notification.adapter.quarkus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.Authenticated;
import tech.kayys.numou.notification.application.AuthenticatedNotificationApi;
import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated push-device management endpoints. The API facade derives
 * tenant and recipient scope from NotificationRequestContext; no scope fields
 * are accepted from the HTTP client.
 */
@Path("/api/v1/notifications/devices")
@Authenticated
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PushDeviceResource {
    private final AuthenticatedNotificationApi notifications;

    @jakarta.inject.Inject
    public PushDeviceResource(AuthenticatedNotificationApi notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications is required");
    }

    @POST
    public Response register(@Valid RegisterDeviceBody request) {
        var device = notifications.registerDevice(new AuthenticatedNotificationApi.RegisterDeviceRequest(
                request.token(), request.platform(), request.appInstance()));
        return Response.ok(DeviceResponse.from(device)).build();
    }

    @GET
    public List<DeviceResponse> list() {
        return notifications.activeDevices().stream().map(DeviceResponse::from).toList();
    }

    @DELETE
    @Path("/{deviceId}")
    public Response unregister(@PathParam("deviceId") UUID deviceId) {
        return notifications.unregisterDevice(deviceId)
                .map(DeviceResponse::from)
                .map(device -> Response.ok(device).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    public record RegisterDeviceBody(
            @NotBlank @Size(max = PushDevice.MAX_TOKEN_LENGTH) String token,
            @NotNull PushDevice.Platform platform,
            @Size(max = PushDevice.MAX_APP_INSTANCE_LENGTH) String appInstance) {
    }

    /** Deliberately excludes token, tenant, recipient, and encryption metadata. */
    public record DeviceResponse(
            UUID id,
            PushDevice.Platform platform,
            String appInstance,
            Instant createdAt,
            Instant updatedAt) {
        static DeviceResponse from(AuthenticatedNotificationApi.DeviceView view) {
            return new DeviceResponse(view.id(), view.platform(), view.appInstance(),
                    view.createdAt(), view.updatedAt());
        }
    }
}
