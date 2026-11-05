package tech.kayys.numou.advertising.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingCampaignStore;
import javax.sql.DataSource;
import java.net.URI;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Tenant-scoped PostgreSQL persistence for campaigns, creatives and assignments. */
@ApplicationScoped
public class PostgresAdvertisingCampaignStore implements AdvertisingCampaignStore {
  private final DataSource dataSource;
  @Inject public PostgresAdvertisingCampaignStore(DataSource dataSource) {
    this.dataSource=Objects.requireNonNull(dataSource);
  }

  @Override public CompletionStage<Campaign> saveCampaign(Campaign c) {
    return execute(()->{
      String sql=c.version()==0
        ? "INSERT INTO advertising_campaign(tenant_id,campaign_id,advertiser_account_id,campaign_name,objective,starts_at,ends_at,campaign_status,created_at,version) VALUES(?,?,?,?,?,?,?,?,?,0)"
        : "UPDATE advertising_campaign SET campaign_status=?,version=? WHERE tenant_id=? AND campaign_id=? AND version=?";
      try(Connection db=dataSource.getConnection();PreparedStatement s=db.prepareStatement(sql)){
        if(c.version()==0){
          s.setString(1,c.tenantId().value());s.setObject(2,c.id().value());s.setObject(3,c.advertiserId().value());
          s.setString(4,c.name());s.setString(5,c.objective().name());s.setTimestamp(6,Timestamp.from(c.startsAt()));
          if(c.endsAt()==null)s.setNull(7,Types.TIMESTAMP_WITH_TIMEZONE);else s.setTimestamp(7,Timestamp.from(c.endsAt()));
          s.setString(8,c.status().name());s.setTimestamp(9,Timestamp.from(c.createdAt()));
        } else {
          s.setString(1,c.status().name());s.setLong(2,c.version());s.setString(3,c.tenantId().value());
          s.setObject(4,c.id().value());s.setLong(5,c.version()-1);
        }
        requireOne(s.executeUpdate(),"campaign insert or stale update");return c;
      }
    });
  }

  @Override public CompletionStage<Optional<Campaign>> findCampaign(TenantId tenant,CampaignId id) {
    return execute(()->{
      String sql="SELECT campaign_id,advertiser_account_id,campaign_name,objective,starts_at,ends_at,campaign_status,created_at,version FROM advertising_campaign WHERE tenant_id=? AND campaign_id=?";
      try(Connection db=dataSource.getConnection();PreparedStatement s=db.prepareStatement(sql)){
        s.setString(1,tenant.value());s.setObject(2,id.value());
        try(ResultSet r=s.executeQuery()){
          if(!r.next())return Optional.empty();
          Timestamp ends=r.getTimestamp(6);
          return Optional.of(new Campaign(new CampaignId(r.getObject(1,UUID.class)),tenant,
            new AdvertisingAccountId(r.getObject(2,UUID.class)),r.getString(3),CampaignObjective.valueOf(r.getString(4)),
            r.getTimestamp(5).toInstant(),ends==null?null:ends.toInstant(),CampaignStatus.valueOf(r.getString(7)),
            r.getTimestamp(8).toInstant(),r.getLong(9)));
        }
      }
    });
  }

  @Override public CompletionStage<Creative> saveCreative(Creative c) {
    return execute(()->{
      String sql=c.version()==0
        ? "INSERT INTO advertising_creative(tenant_id,creative_id,advertiser_account_id,creative_name,creative_format,headline,body,destination_uri,document_reference,creative_status,review_note,created_at,version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0)"
        : "UPDATE advertising_creative SET creative_status=?,review_note=?,version=? WHERE tenant_id=? AND creative_id=? AND version=?";
      try(Connection db=dataSource.getConnection();PreparedStatement s=db.prepareStatement(sql)){
        if(c.version()==0){
          s.setString(1,c.tenantId().value());s.setObject(2,c.id().value());s.setObject(3,c.advertiserId().value());
          s.setString(4,c.name());s.setString(5,c.format().name());s.setString(6,c.headline());s.setString(7,c.body());
          s.setString(8,c.destinationUri()==null?null:c.destinationUri().toString());s.setString(9,c.documentReference());
          s.setString(10,c.status().name());s.setString(11,c.reviewNote());s.setTimestamp(12,Timestamp.from(c.createdAt()));
        } else {
          s.setString(1,c.status().name());s.setString(2,c.reviewNote());s.setLong(3,c.version());
          s.setString(4,c.tenantId().value());s.setObject(5,c.id().value());s.setLong(6,c.version()-1);
        }
        requireOne(s.executeUpdate(),"creative insert or stale update");return c;
      }
    });
  }

  @Override public CompletionStage<Optional<Creative>> findCreative(TenantId tenant,CreativeId id) {
    return execute(()->{
      String sql="SELECT creative_id,advertiser_account_id,creative_name,creative_format,headline,body,destination_uri,document_reference,creative_status,review_note,created_at,version FROM advertising_creative WHERE tenant_id=? AND creative_id=?";
      try(Connection db=dataSource.getConnection();PreparedStatement s=db.prepareStatement(sql)){
        s.setString(1,tenant.value());s.setObject(2,id.value());
        try(ResultSet r=s.executeQuery()){
          if(!r.next())return Optional.empty();
          String destination=r.getString(7);
          return Optional.of(new Creative(new CreativeId(r.getObject(1,UUID.class)),tenant,
            new AdvertisingAccountId(r.getObject(2,UUID.class)),r.getString(3),CreativeFormat.valueOf(r.getString(4)),
            r.getString(5),r.getString(6),destination==null?null:URI.create(destination),r.getString(8),
            CreativeStatus.valueOf(r.getString(9)),r.getString(10),r.getTimestamp(11).toInstant(),r.getLong(12)));
        }
      }
    });
  }

  @Override public CompletionStage<CampaignCreativeAssignment> assignCreative(CampaignCreativeAssignment a) {
    return execute(()->{
      String sql="INSERT INTO advertising_campaign_creative(tenant_id,campaign_id,creative_id,assigned_at) VALUES(?,?,?,?)";
      try(Connection db=dataSource.getConnection();PreparedStatement s=db.prepareStatement(sql)){
        s.setString(1,a.tenantId().value());s.setObject(2,a.campaignId().value());s.setObject(3,a.creativeId().value());
        s.setTimestamp(4,Timestamp.from(a.assignedAt()));requireOne(s.executeUpdate(),"creative assignment");return a;
      }
    });
  }

  @Override public CompletionStage<List<Creative>> findAssignedCreatives(TenantId tenant,CampaignId campaignId) {
    return execute(()->{
      String sql="SELECT c.creative_id,c.advertiser_account_id,c.creative_name,c.creative_format,c.headline,c.body,c.destination_uri,c.document_reference,c.creative_status,c.review_note,c.created_at,c.version FROM advertising_creative c JOIN advertising_campaign_creative a ON a.tenant_id=c.tenant_id AND a.creative_id=c.creative_id WHERE a.tenant_id=? AND a.campaign_id=? ORDER BY a.assigned_at,c.creative_id";
      try(Connection db=dataSource.getConnection();PreparedStatement s=db.prepareStatement(sql)){
        s.setString(1,tenant.value());s.setObject(2,campaignId.value());
        try(ResultSet r=s.executeQuery()){
          List<Creative> result=new ArrayList<>();
          while(r.next()){
            String destination=r.getString(7);
            result.add(new Creative(new CreativeId(r.getObject(1,UUID.class)),tenant,
              new AdvertisingAccountId(r.getObject(2,UUID.class)),r.getString(3),CreativeFormat.valueOf(r.getString(4)),
              r.getString(5),r.getString(6),destination==null?null:URI.create(destination),r.getString(8),
              CreativeStatus.valueOf(r.getString(9)),r.getString(10),r.getTimestamp(11).toInstant(),r.getLong(12)));
          }
          return List.copyOf(result);
        }
      }
    });
  }

  private static void requireOne(int count,String operation) {
    if(count!=1)throw new IllegalStateException(operation+" failed: record missing, duplicate, or stale version");
  }
  private static <T> CompletionStage<T> execute(SqlSupplier<T> work) {
    try{return CompletableFuture.completedFuture(work.get());}
    catch(SQLException e){return CompletableFuture.failedFuture(new IllegalStateException("Advertising PostgreSQL persistence failed",e));}
    catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
  }
  @FunctionalInterface private interface SqlSupplier<T>{T get() throws SQLException;}
}
