package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped JDBC persistence for tariff snapshots and COD operational facts. */
@ApplicationScoped
public class JdbcCourierCommercialRepository {
    private final DataSource dataSource;
    @Inject public JdbcCourierCommercialRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public CompletionStage<CourierRateCard> saveRateCard(TenantId tenantId, CourierRateCard card) {
        if (tenantId == null || card == null) return failed(new IllegalArgumentException("tenantId and card are required"));
        if (!tenantId.equals(card.tenantId())) return failed(new SecurityException("rate card tenant mismatch"));
        String sql = """
            INSERT INTO courier_rate_cards (tenant_id,id,version,service_level,origin_zone,destination_zone,currency_code,
              base_fee,per_chargeable_kg,dimensional_divisor_cm3_per_kg,tax_rate,effective_from,effective_until)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (tenant_id,id) DO NOTHING
            """;
        try (Connection c=dataSource.getConnection(); PreparedStatement s=c.prepareStatement(sql)) {
            s.setObject(1,tenantId.value()); s.setObject(2,card.id()); s.setString(3,card.version());
            s.setString(4,card.serviceLevel().name()); s.setString(5,card.originZone()); s.setString(6,card.destinationZone());
            s.setString(7,card.currency().getCurrencyCode()); s.setBigDecimal(8,card.baseFee());
            s.setBigDecimal(9,card.perChargeableKg()); s.setBigDecimal(10,card.dimensionalDivisorCm3PerKg());
            s.setBigDecimal(11,card.taxRate()); s.setTimestamp(12,Timestamp.from(card.effectiveFrom()));
            if(card.effectiveUntil()==null)s.setNull(13,Types.TIMESTAMP_WITH_TIMEZONE);else s.setTimestamp(13,Timestamp.from(card.effectiveUntil()));
            if(s.executeUpdate()!=1) return failed(new IllegalStateException("rate card IDs are immutable; publish a new version"));
            return CompletableFuture.completedFuture(card);
        } catch(SQLException e) { return failed(new IllegalStateException("Unable to save rate card",e)); }
    }

    public CompletionStage<Optional<CourierRateCard>> findEffectiveRateCard(TenantId tenantId, ServiceLevel service,
            String originZone, String destinationZone, Instant at) {
        if(tenantId==null||service==null||originZone==null||destinationZone==null||at==null)
            return failed(new IllegalArgumentException("tenant, service, zones and instant are required"));
        String sql = """
            SELECT id,version,service_level,origin_zone,destination_zone,currency_code,base_fee,per_chargeable_kg,
                   dimensional_divisor_cm3_per_kg,tax_rate,effective_from,effective_until
            FROM courier_rate_cards WHERE tenant_id=? AND service_level=? AND origin_zone=upper(trim(?))
              AND destination_zone=upper(trim(?)) AND effective_from<=? AND (effective_until IS NULL OR effective_until>?)
            ORDER BY effective_from DESC
            """;
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
            s.setObject(1,tenantId.value());s.setString(2,service.name());s.setString(3,originZone);s.setString(4,destinationZone);
            s.setTimestamp(5,Timestamp.from(at));s.setTimestamp(6,Timestamp.from(at));
            try(ResultSet rs=s.executeQuery()){
                if(!rs.next())return CompletableFuture.completedFuture(Optional.empty());
                CourierRateCard card=new CourierRateCard(rs.getObject("id",UUID.class),tenantId,rs.getString("version"),
                    ServiceLevel.valueOf(rs.getString("service_level")),rs.getString("origin_zone"),rs.getString("destination_zone"),
                    Currency.getInstance(rs.getString("currency_code")),rs.getBigDecimal("base_fee"),rs.getBigDecimal("per_chargeable_kg"),
                    rs.getBigDecimal("dimensional_divisor_cm3_per_kg"),rs.getBigDecimal("tax_rate"),
                    rs.getTimestamp("effective_from").toInstant(),rs.getTimestamp("effective_until")==null?null:rs.getTimestamp("effective_until").toInstant());
                if(rs.next()) return failed(new IllegalStateException("overlapping effective rate cards found for route and service"));
                return CompletableFuture.completedFuture(Optional.of(card));
            }
        }catch(SQLException|RuntimeException e){return failed(new IllegalStateException("Unable to find effective rate card",e));}
    }

    public CompletionStage<ShipmentQuote> saveQuote(TenantId tenantId, ShipmentQuote quote) {
        if(tenantId==null||quote==null)return failed(new IllegalArgumentException("tenantId and quote are required"));
        String sql=""" 
            INSERT INTO courier_shipment_quotes (tenant_id,id,shipment_id,rate_card_id,rate_card_version,currency_code,
              actual_weight_kg,dimensional_weight_kg,chargeable_weight_kg,base_fee,weight_fee,tax,total,quoted_at,expires_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
            s.setObject(1,tenantId.value());s.setObject(2,quote.id());s.setObject(3,quote.shipmentId().value());
            s.setObject(4,quote.rateCardId());s.setString(5,quote.rateCardVersion());s.setString(6,quote.currency().getCurrencyCode());
            s.setBigDecimal(7,quote.actualWeightKg());s.setBigDecimal(8,quote.dimensionalWeightKg());s.setBigDecimal(9,quote.chargeableWeightKg());
            s.setBigDecimal(10,quote.baseFee());s.setBigDecimal(11,quote.weightFee());s.setBigDecimal(12,quote.tax());s.setBigDecimal(13,quote.total());
            s.setTimestamp(14,Timestamp.from(quote.quotedAt()));if(quote.expiresAt()==null)s.setNull(15,Types.TIMESTAMP_WITH_TIMEZONE);else s.setTimestamp(15,Timestamp.from(quote.expiresAt()));
            s.executeUpdate();return CompletableFuture.completedFuture(quote);
        }catch(SQLException e){return failed(new IllegalStateException("Unable to save quote snapshot",e));}
    }

    public CompletionStage<CodObligation> saveCodObligation(TenantId tenantId, CodObligation cod) {
        if(tenantId==null||cod==null)return failed(new IllegalArgumentException("tenantId and COD obligation are required"));
        if(!tenantId.equals(cod.tenantId()))return failed(new SecurityException("COD tenant mismatch"));
        String sql="""
            INSERT INTO courier_cod_obligations (tenant_id,id,shipment_id,amount,currency_code,status,collector_reference,
              collection_branch_code,collected_amount,collected_at,settlement_reference,settled_amount,variance,settled_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())
            ON CONFLICT (tenant_id,id) DO UPDATE SET status=EXCLUDED.status,collector_reference=EXCLUDED.collector_reference,
              collection_branch_code=EXCLUDED.collection_branch_code,collected_amount=EXCLUDED.collected_amount,collected_at=EXCLUDED.collected_at,
              settlement_reference=EXCLUDED.settlement_reference,settled_amount=EXCLUDED.settled_amount,variance=EXCLUDED.variance,
              settled_at=EXCLUDED.settled_at,updated_at=now()
            WHERE courier_cod_obligations.shipment_id=EXCLUDED.shipment_id
              AND courier_cod_obligations.amount=EXCLUDED.amount AND courier_cod_obligations.currency_code=EXCLUDED.currency_code
              AND (courier_cod_obligations.status=EXCLUDED.status
                OR (courier_cod_obligations.status='DUE' AND EXCLUDED.status='COLLECTED')
                OR (courier_cod_obligations.status='COLLECTED' AND EXCLUDED.status IN ('SETTLED','VARIANCE_RECORDED')))
            """;
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
            s.setObject(1,tenantId.value());s.setObject(2,cod.id());s.setObject(3,cod.shipmentId().value());s.setBigDecimal(4,cod.amount());
            s.setString(5,cod.currency().getCurrencyCode());s.setString(6,cod.status().name());s.setString(7,cod.collectorReference());
            s.setString(8,cod.collectionBranchCode());setDecimal(s,9,cod.collectedAmount());setInstant(s,10,cod.collectedAt());
            s.setString(11,cod.settlementReference());setDecimal(s,12,cod.settledAmount());setDecimal(s,13,cod.variance());setInstant(s,14,cod.settledAt());
            s.setTimestamp(15,Timestamp.from(cod.createdAt()));
            if(s.executeUpdate()!=1)return failed(new IllegalStateException("COD lifecycle regression or immutable-field mutation rejected"));
            return CompletableFuture.completedFuture(cod);
        }catch(SQLException e){return failed(new IllegalStateException("Unable to save COD obligation",e));}
    }
    private static void setDecimal(PreparedStatement s,int index,java.math.BigDecimal value)throws SQLException{if(value==null)s.setNull(index,Types.NUMERIC);else s.setBigDecimal(index,value);}
    private static void setInstant(PreparedStatement s,int index,Instant value)throws SQLException{if(value==null)s.setNull(index,Types.TIMESTAMP_WITH_TIMEZONE);else s.setTimestamp(index,Timestamp.from(value));}
    private static <T> CompletionStage<T> failed(Throwable e){return CompletableFuture.failedFuture(e);}
}
