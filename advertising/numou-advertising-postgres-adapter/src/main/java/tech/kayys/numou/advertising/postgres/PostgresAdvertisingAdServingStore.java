package tech.kayys.numou.advertising.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingAdServingStore;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** PostgreSQL serving read model and append-only decision audit adapter. JDBC is blocking. */
@ApplicationScoped
public class PostgresAdvertisingAdServingStore implements AdvertisingAdServingStore {
  private final DataSource dataSource;
  @Inject public PostgresAdvertisingAdServingStore(DataSource dataSource) {
    this.dataSource=Objects.requireNonNull(dataSource);
  }

  @Override public CompletionStage<Optional<AdServingInventory>> findInventory(TenantId tenant,AdPlacementId placementId) {
    return execute(()->{
      String sql="SELECT p.placement_id,p.property_id,p.placement_code,p.format,p.placement_status,p.created_at,p.version,"+
        "pr.property_id,pr.publisher_account_id,pr.property_name,pr.canonical_uri,pr.property_status,pr.created_at,pr.version,"+
        "pu.account_id,pu.party_reference,pu.display_name,pu.account_status,pu.created_at,pu.version "+
        "FROM advertising_ad_placement p "+
        "JOIN advertising_publisher_property pr ON pr.tenant_id=p.tenant_id AND pr.property_id=p.property_id "+
        "JOIN advertising_publisher_account pu ON pu.tenant_id=pr.tenant_id AND pu.account_id=pr.publisher_account_id "+
        "WHERE p.tenant_id=? AND p.placement_id=?";
      try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
        s.setString(1,tenant.value());s.setObject(2,placementId.value());
        try(ResultSet r=s.executeQuery()){
          if(!r.next())return Optional.empty();
          AdPlacement placement=new AdPlacement(new AdPlacementId(r.getObject(1,UUID.class)),tenant,
            new PublisherPropertyId(r.getObject(2,UUID.class)),r.getString(3),r.getString(4),
            PlacementStatus.valueOf(r.getString(5)),r.getTimestamp(6).toInstant(),r.getLong(7));
          PublisherProperty property=new PublisherProperty(new PublisherPropertyId(r.getObject(8,UUID.class)),tenant,
            new AdvertisingAccountId(r.getObject(9,UUID.class)),r.getString(10),URI.create(r.getString(11)),
            PropertyStatus.valueOf(r.getString(12)),r.getTimestamp(13).toInstant(),r.getLong(14));
          PublisherAccount publisher=new PublisherAccount(new AdvertisingAccountId(r.getObject(15,UUID.class)),tenant,
            new PartyReference(r.getString(16)),r.getString(17),AccountStatus.valueOf(r.getString(18)),
            r.getTimestamp(19).toInstant(),r.getLong(20));
          return Optional.of(new AdServingInventory(placement,property,publisher));
        }
      }
    });
  }

  @Override public CompletionStage<List<AdServingCandidate>> findEligibleCandidates(TenantId tenant,AdPlacementId placementId,
                                                                                     String format,Instant at,int limit) {
    return execute(()->{
      String sql="SELECT ca.campaign_id,ca.advertiser_account_id,ca.campaign_name,ca.objective,ca.starts_at,ca.ends_at,"+
        "ca.campaign_status,ca.created_at,ca.version,"+
        "ad.account_id,ad.party_reference,ad.display_name,ad.account_status,ad.created_at,ad.version,"+
        "cr.creative_id,cr.creative_name,cr.creative_format,cr.headline,cr.body,cr.destination_uri,cr.document_reference,"+
        "cr.creative_status,cr.review_note,cr.created_at,cr.version "+
        "FROM advertising_campaign_creative cc "+
        "JOIN advertising_campaign_placement cp ON cp.tenant_id=cc.tenant_id AND cp.campaign_id=cc.campaign_id AND cp.placement_id=? "+
        "JOIN advertising_campaign ca ON ca.tenant_id=cc.tenant_id AND ca.campaign_id=cc.campaign_id "+
        "JOIN advertising_advertiser_account ad ON ad.tenant_id=ca.tenant_id AND ad.account_id=ca.advertiser_account_id "+
        "JOIN advertising_creative cr ON cr.tenant_id=cc.tenant_id AND cr.creative_id=cc.creative_id "+
        "JOIN advertising_ad_placement pl ON pl.tenant_id=cc.tenant_id AND pl.placement_id=? AND pl.placement_status='ENABLED' "+
        "JOIN advertising_publisher_property pr ON pr.tenant_id=pl.tenant_id AND pr.property_id=pl.property_id AND pr.property_status='VERIFIED' "+
        "JOIN advertising_publisher_account pu ON pu.tenant_id=pr.tenant_id AND pu.account_id=pr.publisher_account_id AND pu.account_status='ACTIVE' "+
        "WHERE cc.tenant_id=? AND ca.campaign_status='ACTIVE' AND ca.starts_at<=? "+
        "AND (ca.ends_at IS NULL OR ca.ends_at>?) AND ad.account_status='ACTIVE' "+
        "AND cr.creative_status='APPROVED' AND cr.creative_format=? AND upper(pl.format)=? "+
        "ORDER BY ca.created_at,ca.campaign_id,cr.creative_id LIMIT ?";
      try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
        s.setObject(1,placementId.value());s.setObject(2,placementId.value());s.setString(3,tenant.value());s.setTimestamp(4,Timestamp.from(at));
        s.setTimestamp(5,Timestamp.from(at));s.setString(6,format.toUpperCase(Locale.ROOT));s.setString(7,format.toUpperCase(Locale.ROOT));s.setInt(8,Math.max(1,Math.min(limit,100)));
        try(ResultSet r=s.executeQuery()){
          List<AdServingCandidate> result=new ArrayList<>();
          while(r.next()){
            Timestamp ends=r.getTimestamp(6);
            Campaign campaign=new Campaign(new CampaignId(r.getObject(1,UUID.class)),tenant,
              new AdvertisingAccountId(r.getObject(2,UUID.class)),r.getString(3),CampaignObjective.valueOf(r.getString(4)),
              r.getTimestamp(5).toInstant(),ends==null?null:ends.toInstant(),CampaignStatus.valueOf(r.getString(7)),
              r.getTimestamp(8).toInstant(),r.getLong(9));
            AdvertiserAccount advertiser=new AdvertiserAccount(new AdvertisingAccountId(r.getObject(10,UUID.class)),tenant,
              new PartyReference(r.getString(11)),r.getString(12),AccountStatus.valueOf(r.getString(13)),
              r.getTimestamp(14).toInstant(),r.getLong(15));
            String destination=r.getString(21);
            Creative creative=new Creative(new CreativeId(r.getObject(16,UUID.class)),tenant,advertiser.id(),
              r.getString(17),CreativeFormat.valueOf(r.getString(18)),r.getString(19),r.getString(20),
              destination==null?null:URI.create(destination),r.getString(22),CreativeStatus.valueOf(r.getString(23)),
              r.getString(24),r.getTimestamp(25).toInstant(),r.getLong(26));
            result.add(new AdServingCandidate(campaign,advertiser,creative));
          }
          return List.copyOf(result);
        }
      }
    });
  }

  @Override public CompletionStage<AdDecision> recordDecision(AdDecision d) {
    return execute(()->{
      String sql="INSERT INTO advertising_ad_decision(tenant_id,decision_id,placement_id,property_id,campaign_id,advertiser_account_id,creative_id,decision_status,decision_reason,decided_at) VALUES(?,?,?,?,?,?,?,?,?,?)";
      try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
        s.setString(1,d.tenantId().value());s.setObject(2,d.id());
        setUuid(s,3,d.placementId()==null?null:d.placementId().value());
        setUuid(s,4,d.propertyId()==null?null:d.propertyId().value());
        setUuid(s,5,d.campaignId()==null?null:d.campaignId().value());
        setUuid(s,6,d.advertiserId()==null?null:d.advertiserId().value());
        setUuid(s,7,d.creativeId()==null?null:d.creativeId().value());
        s.setString(8,d.status().name());s.setString(9,d.reason().name());s.setTimestamp(10,Timestamp.from(d.decidedAt()));
        if(s.executeUpdate()!=1)throw new IllegalStateException("ad decision audit insert failed");
        return d;
      }
    });
  }

  private static void setUuid(PreparedStatement s,int index,UUID value)throws SQLException {
    if(value==null)s.setNull(index,Types.OTHER);else s.setObject(index,value);
  }
  private static <T> CompletionStage<T> execute(SqlSupplier<T> work) {
    try{return CompletableFuture.completedFuture(work.get());}
    catch(SQLException e){return CompletableFuture.failedFuture(new IllegalStateException("Advertising serving persistence failed",e));}
    catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
  }
  @FunctionalInterface private interface SqlSupplier<T>{T get()throws SQLException;}
}
