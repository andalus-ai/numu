package id.mindo.notification.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import id.mindo.notification.dto.DeviceRegistrationRequest;
import id.mindo.notification.dto.NotificationMessage;
import id.mindo.notification.dto.PushDeviceSummary;
import id.mindo.notification.service.DeviceRegistrationService;
import id.mindo.notification.service.NotificationAuthService;
import id.mindo.notification.service.PushDispatchService;
import id.mindo.notification.service.WebSocketService;

import java.util.List;
import java.util.Map;

@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    WebSocketService webSocketService;

    @Inject
    DeviceRegistrationService deviceRegistrationService;

    @Inject
    PushDispatchService pushDispatchService;

    @Inject
    NotificationAuthService notificationAuthService;

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
                "status", "ok",
                "fcmEnabled", pushDispatchService != null && pushDispatchService.isFcmEnabled(),
                "fcmConfigured", pushDispatchService != null && pushDispatchService.isFcmConfigured(),
                "registeredClients",
                deviceRegistrationService == null ? 0 : deviceRegistrationService.registeredClientCount(),
                "registeredDevices",
                deviceRegistrationService == null ? 0 : deviceRegistrationService.registeredDeviceCount())).build();
    }

    @POST
    @Path("/send")
    public Response sendNotification(
            @HeaderParam("X-Notification-Key") String notificationKey,
            NotificationRequest request) {
        if (!notificationAuthService.isInternalSendAllowed(notificationKey)) {
            return error(Response.Status.UNAUTHORIZED, "Notification service key is required");
        }
        if (request == null) {
            return error(Response.Status.BAD_REQUEST, "Notification request is required");
        }
        try {
            NotificationMessage notification = new NotificationMessage(
                    request.getType(),
                    request.getTitle(),
                    request.getMessage(),
                    request.getData());

            if (request.getClientId() != null) {
                webSocketService.sendNotification(request.getClientId(), notification);
                int pushTargets = pushDispatchService.dispatchToClient(request.getClientId(), notification);
                return Response.ok(
                        Map.of(
                                "status", "success",
                                "message", "Notification dispatched",
                                "pushTargets", pushTargets))
                        .build();
            } else {
                webSocketService.broadcastNotification(notification);
                return Response.ok(Map.of("status", "success", "message", "Notification broadcast")).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("status", "error", "message", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/devices/register")
    public Response registerDevice(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            DeviceRegistrationRequest request) {
        final Response authFailure = validateDeviceAccess(authorization, request);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            final var record = deviceRegistrationService.register(request);
            return Response.ok(Map.of(
                    "status", "success",
                    "clientId", record.getClientId(),
                    "platform", record.getPlatform(),
                    "updatedAt", record.getUpdatedAt().toString())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("status", "error", "message", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/devices/unregister")
    public Response unregisterDevice(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            DeviceRegistrationRequest request) {
        final Response authFailure = validateDeviceAccess(authorization, request);
        if (authFailure != null) {
            return authFailure;
        }
        final boolean removed = deviceRegistrationService.unregister(
                request.getClientId(),
                request.getToken());
        return Response.ok(Map.of("status", "success", "removed", removed)).build();
    }

    @GET
    @Path("/devices/{clientId}")
    public Response listDevices(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("clientId") String clientId) {
        final Response authFailure = validateDeviceAccess(authorization, clientId);
        if (authFailure != null) {
            return authFailure;
        }
        List<PushDeviceSummary> devices = deviceRegistrationService.listSummariesByClientId(clientId);
        return Response.ok(Map.of(
                "status", "success",
                "clientId", clientId,
                "count", devices.size(),
                "devices", devices)).build();
    }

    private Response validateDeviceAccess(String authorization, DeviceRegistrationRequest request) {
        if (request == null) {
            return error(Response.Status.BAD_REQUEST, "Device registration request is required");
        }
        return validateDeviceAccess(authorization, request.getClientId());
    }

    private Response validateDeviceAccess(String authorization, String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "clientId is required");
        }
        if (!notificationAuthService.requiresDeviceAuth()) {
            return null;
        }
        final var principal = notificationAuthService.principalFromAuthorization(authorization);
        if (principal.isEmpty()) {
            return error(Response.Status.UNAUTHORIZED, "Bearer token is required");
        }
        if (!notificationAuthService.principalCanAccessClient(principal.get(), clientId)) {
            return error(Response.Status.FORBIDDEN, "Bearer token does not match clientId");
        }
        return null;
    }

    private Response error(Response.Status status, String message) {
        return Response.status(status)
                .entity(Map.of("status", "error", "message", message))
                .build();
    }

    public static class NotificationRequest {
        private String clientId;
        private String type = "info";
        private String title = "Notification";
        private String message;
        private Map<String, Object> data;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }
    }
}
