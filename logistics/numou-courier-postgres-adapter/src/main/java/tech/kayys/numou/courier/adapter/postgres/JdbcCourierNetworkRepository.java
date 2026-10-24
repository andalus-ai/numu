package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.domain.NetworkPartner;
import tech.kayys.numou.courier.domain.PartnerShipmentReference;
import tech.kayys.numou.courier.domain.PartnerWebhookEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit persistence for partner directory, external references and idempotent webhook inbox. */
@ApplicationScoped
public class JdbcCourierNetworkRepository {
    private final DataSource dataSource;
    @Inject public JdbcCourierNetworkRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public CompletionStage<NetworkPartner> registerPartner(TenantId tenant, NetworkPartner partner) {
        if (tenant == null || partner == null) return failed(new IllegalArgumentException("tenant and partner are required"));
        if (!tenant.equals(partner.tenantId())) return failed(new SecurityException("partner tenant mismatch"));
        String sql = """
            INSERT INTO courier_network_partners
              (tenant_id,id,partner_code,display_name,status,supported_services,created_at,updated_at)
            VALUES (?,?,?,?,?,?::jsonb,?,?)
            ON CONFLICT (tenant_id,id) DO NOTHING
            """;
        try (Connection c=dataSource.getConnection(); PreparedStatement s=c.prepareStatement(sql)) {
            s.setObject(1,tenant.value());s.setObject(2,partner.id());s.setString(3,partner.partnerCode());
            s.setString(4,partner.displayName());s.setString(5,partner.status().name());
            s.setString(6,toJsonArray(partner.supportedServices()));s.setTimestamp(7,Timestamp.from(partner.createdAt()));
            s.setTimestamp(8,Timestamp.from(partner.updatedAt()));
            if(s.executeUpdate()!=1)return failed(new IllegalStateException("partner ID is immutable; use a controlled lifecycle update"));
            return CompletableFuture.completedFuture(partner);
        } catch(SQLException e) { return failed(new IllegalStateException("Unable to register courier partner",e)); }
    }

    /** Returns false for an already-seen event key; callers should acknowledge duplicate deliveries safely. */
    public CompletionStage<Boolean> recordInboundEvent(TenantId tenant, PartnerWebhookEvent event) {
        if(tenant==null||event==null)return failed(new IllegalArgumentException("tenant and event are required"));
        if(!tenant.equals(event.tenantId()))return failed(new SecurityException("partner event tenant mismatch"));
        String sql = """
            INSERT INTO courier_partner_inbound_events
              (tenant_id,id,partner_id,external_event_id,event_type,payload,occurred_at,received_at,processing_status)
            VALUES (?,?,?,?,?,?::jsonb,?,?, 'RECEIVED')
            ON CONFLICT (tenant_id,partner_id,external_event_id) DO NOTHING
            """;
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
            s.setObject(1,tenant.value());s.setObject(2,event.id());s.setObject(3,event.partnerId());
            s.setString(4,event.externalEventId());s.setString(5,event.eventType());s.setString(6,event.payloadJson());
            s.setTimestamp(7,Timestamp.from(event.occurredAt()));s.setTimestamp(8,Timestamp.from(event.receivedAt()));
            return CompletableFuture.completedFuture(s.executeUpdate()==1);
        }catch(SQLException e){return failed(new IllegalStateException("Unable to record partner inbox event",e));}
    }

    public CompletionStage<PartnerShipmentReference> linkExternalShipment(TenantId tenant, PartnerShipmentReference ref) {
        if(tenant==null||ref==null)return failed(new IllegalArgumentException("tenant and reference are required"));
        if(!tenant.equals(ref.tenantId()))return failed(new SecurityException("shipment reference tenant mismatch"));
        String sql="""
            INSERT INTO courier_partner_shipment_references
              (tenant_id,id,partner_id,shipment_id,external_reference,linked_at)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT (tenant_id,id) DO NOTHING
            """;
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
            s.setObject(1,tenant.value());s.setObject(2,ref.id());s.setObject(3,ref.partnerId());
            s.setObject(4,ref.shipmentId().value());s.setString(5,ref.externalReference());s.setTimestamp(6,Timestamp.from(ref.linkedAt()));
            if(s.executeUpdate()!=1)return failed(new IllegalStateException("external reference ID is immutable"));
            return CompletableFuture.completedFuture(ref);
        }catch(SQLException e){return failed(new IllegalStateException("Unable to link partner shipment reference",e));}
    }

    private static String toJsonArray(java.util.Set<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).map(v -> "\"" + v.replace("\\","\\\\").replace("\"","\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
    private static <T> CompletionStage<T> failed(Throwable e){return CompletableFuture.failedFuture(e);}
}