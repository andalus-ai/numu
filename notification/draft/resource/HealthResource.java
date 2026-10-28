package id.mindo.notification.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import id.mindo.notification.service.DeviceRegistrationService;
import id.mindo.notification.service.PushDispatchService;

import jakarta.inject.Inject;
import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    PushDispatchService pushDispatchService;

    @Inject
    DeviceRegistrationService deviceRegistrationService;

    @GET
    public Response health() {
        return Response.ok(healthPayload()).build();
    }

    Map<String, Object> healthPayload() {
        return Map.of(
                "status", "ok",
                "fcmEnabled", pushDispatchService != null && pushDispatchService.isFcmEnabled(),
                "fcmConfigured", pushDispatchService != null && pushDispatchService.isFcmConfigured(),
                "registeredClients",
                deviceRegistrationService == null ? 0 : deviceRegistrationService.registeredClientCount(),
                "registeredDevices",
                deviceRegistrationService == null ? 0 : deviceRegistrationService.registeredDeviceCount());
    }
}
