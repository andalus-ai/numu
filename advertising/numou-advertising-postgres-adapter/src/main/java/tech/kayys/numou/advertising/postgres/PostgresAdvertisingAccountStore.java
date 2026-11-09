package tech.kayys.numou.advertising.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingAccountStore;\nimport tech.kayys.numou.advertising.spi.AdvertisingPage;\nimport tech.kayys.numou.advertising.spi.AdvertisingPageRequest;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tenant-scoped PostgreSQL adapter. Every read and write includes tenant_id.
 * Updates are compare-and-set operations using the previous aggregate version.
 */
@ApplicationScoped
public class PostgresAdvertisingAccountStore implements AdvertisingAccountStore {
    private final DataSource dataSource;

    @Inject
    public PostgresAdvertisingAccountStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override public CompletionStage<AdvertiserAccount> saveAdvertiser(AdvertiserAccount a) {
        return execute(() -> {
            String sql = a.version() == 0
                ? "INSERT INTO advertising_advertiser_account(tenant_id,account_id,party_reference,display_name,account_status,created_at,version) VALUES(?,?,?,?,?,?,0)"
                : "UPDATE advertising_advertiser_account SET account_status=?,version=? WHERE tenant_id=? AND account_id=? AND version=?";
            try (Connection c=dataSource.getConnection(); PreparedStatement s=c.prepareStatement(sql)) {
                if (a.version()==0) {
                    s.setString(1,a.tenantId().value()); s.setObject(2,a.id().value()); s.setString(3,a.party().value());
                    s.setString(4,a.displayName()); s.setString(5,a.status().name()); s.setTimestamp(6,Timestamp.from(a.createdAt()));
                } else {
                    s.setString(1,a.status().name()); s.setLong(2,a.version()); s.setString(3,a.tenantId().value());
                    s.setObject(4,a.id().value()); s.setLong(5,a.version()-1);
                }
                requireOne(s.executeUpdate(),"advertiser insert or stale update"); return a;
            }
        });
    }

    @Override public CompletionStage<Optional<AdvertiserAccount>> findAdvertiser(TenantId tenant,AdvertisingAccountId id) {
        return execute(() -> {
            String sql="SELECT account_id,party_reference,display_name,account_status,created_at,version FROM advertising_advertiser_account WHERE tenant_id=? AND account_id=?";
            try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
                s.setString(1,tenant.value());s.setObject(2,id.value());
                try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(new AdvertiserAccount(
                    new AdvertisingAccountId(r.getObject(1,UUID.class)),tenant,new PartyReference(r.getString(2)),
                    r.getString(3),AccountStatus.valueOf(r.getString(4)),r.getTimestamp(5).toInstant(),r.getLong(6))):Optional.empty();}
            }
        });
    }

    @Override public CompletionStage<PublisherAccount> savePublisher(PublisherAccount a) {
        return execute(() -> {
            String sql = a.version() == 0
                ? "INSERT INTO advertising_publisher_account(tenant_id,account_id,party_reference,display_name,account_status,created_at,version) VALUES(?,?,?,?,?,?,0)"
                : "UPDATE advertising_publisher_account SET account_status=?,version=? WHERE tenant_id=? AND account_id=? AND version=?";
            try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
                if(a.version()==0){s.setString(1,a.tenantId().value());s.setObject(2,a.id().value());s.setString(3,a.party().value());s.setString(4,a.displayName());s.setString(5,a.status().name());s.setTimestamp(6,Timestamp.from(a.createdAt()));}
                else{s.setString(1,a.status().name());s.setLong(2,a.version());s.setString(3,a.tenantId().value());s.setObject(4,a.id().value());s.setLong(5,a.version()-1);}
                requireOne(s.executeUpdate(),"publisher insert or stale update");return a;
            }
        });
    }

    @Override public CompletionStage<Optional<PublisherAccount>> findPublisher(TenantId tenant,AdvertisingAccountId id) {
        return execute(() -> {
            String sql="SELECT account_id,party_reference,display_name,account_status,created_at,version FROM advertising_publisher_account WHERE tenant_id=? AND account_id=?";
            try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
                s.setString(1,tenant.value());s.setObject(2,id.value());
                try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(new PublisherAccount(
                    new AdvertisingAccountId(r.getObject(1,UUID.class)),tenant,new PartyReference(r.getString(2)),
                    r.getString(3),AccountStatus.valueOf(r.getString(4)),r.getTimestamp(5).toInstant(),r.getLong(6))):Optional.empty();}
            }
        });
    }

    @Override public CompletionStage<PublisherProperty> saveProperty(PublisherProperty p) {
        return execute(() -> {
            String sql=p.version()==0
                ? "INSERT INTO advertising_publisher_property(tenant_id,property_id,publisher_account_id,property_name,canonical_uri,property_status,created_at,version) VALUES(?,?,?,?,?,?,?,0)"
                : "UPDATE advertising_publisher_property SET property_status=?,version=? WHERE tenant_id=? AND property_id=? AND version=?";
            try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
                if(p.version()==0){s.setString(1,p.tenantId().value());s.setObject(2,p.id().value());s.setObject(3,p.publisherId().value());s.setString(4,p.name());s.setString(5,p.canonicalUri().toString());s.setString(6,p.status().name());s.setTimestamp(7,Timestamp.from(p.createdAt()));}
                else{s.setString(1,p.status().name());s.setLong(2,p.version());s.setString(3,p.tenantId().value());s.setObject(4,p.id().value());s.setLong(5,p.version()-1);}
                requireOne(s.executeUpdate(),"property insert or stale update");return p;
            }
        });
    }

    @Override public CompletionStage<Optional<PublisherProperty>> findProperty(TenantId tenant,PublisherPropertyId id) {
        return execute(() -> {
            String sql="SELECT property_id,publisher_account_id,property_name,canonical_uri,property_status,created_at,version FROM advertising_publisher_property WHERE tenant_id=? AND property_id=?";
            try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
                s.setString(1,tenant.value());s.setObject(2,id.value());
                try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(new PublisherProperty(
                    new PublisherPropertyId(r.getObject(1,UUID.class)),tenant,new AdvertisingAccountId(r.getObject(2,UUID.class)),
                    r.getString(3),URI.create(r.getString(4)),PropertyStatus.valueOf(r.getString(5)),r.getTimestamp(6).toInstant(),r.getLong(7))):Optional.empty();}
            }
        });
    }

    @Override public CompletionStage<AdPlacement> savePlacement(AdPlacement p) {
        return execute(() -> {
            String sql=p.version()==0
                ? "INSERT INTO advertising_ad_placement(tenant_id,placement_id,property_id,placement_code,format,placement_status,created_at,version) VALUES(?,?,?,?,?,?,?,0)"
                : "UPDATE advertising_ad_placement SET placement_status=?,version=? WHERE tenant_id=? AND placement_id=? AND version=?";
            try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
                if(p.version()==0){s.setString(1,p.tenantId().value());s.setObject(2,p.id().value());s.setObject(3,p.propertyId().value());s.setString(4,p.code());s.setString(5,p.format());s.setString(6,p.status().name());s.setTimestamp(7,Timestamp.from(p.createdAt()));}
                else{s.setString(1,p.status().name());s.setLong(2,p.version());s.setString(3,p.tenantId().value());s.setObject(4,p.id().value());s.setLong(5,p.version()-1);}
                requireOne(s.executeUpdate(),"placement insert or stale update");return p;
            }
        });
    }

    @Override public CompletionStage<Optional<AdPlacement>> findPlacement(TenantId tenant,AdPlacementId id) {
        return execute(() -> {
            String sql="SELECT placement_id,property_id,placement_code,format,placement_status,created_at,version FROM advertising_ad_placement WHERE tenant_id=? AND placement_id=?";
            try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
                s.setString(1,tenant.value());s.setObject(2,id.value());
                try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(new AdPlacement(
                    new AdPlacementId(r.getObject(1,UUID.class)),tenant,new PublisherPropertyId(r.getObject(2,UUID.class)),
                    r.getString(3),r.getString(4),PlacementStatus.valueOf(r.getString(5)),r.getTimestamp(6).toInstant(),r.getLong(7))):Optional.empty();}
            }
        });
    }

    @Override public CompletionStage<AdvertisingPage<AdvertiserAccount>> listAdvertisers(TenantId tenant, AdvertisingPageRequest page) {
        return execute(() -> queryPage(tenant,page,
            "SELECT account_id,party_reference,display_name,account_status,created_at,version FROM advertising_advertiser_account WHERE tenant_id=? ORDER BY created_at DESC,account_id ASC",
            "SELECT COUNT(*) FROM advertising_advertiser_account WHERE tenant_id=?",1,
            s -> s.setString(1,tenant.value()),
            r -> new AdvertiserAccount(new AdvertisingAccountId(r.getObject(1,UUID.class)),tenant,
                new PartyReference(r.getString(2)),r.getString(3),AccountStatus.valueOf(r.getString(4)),
                r.getTimestamp(5).toInstant(),r.getLong(6))));
    }

    @Override public CompletionStage<AdvertisingPage<PublisherAccount>> listPublishers(TenantId tenant, AdvertisingPageRequest page) {
        return execute(() -> queryPage(tenant,page,
            "SELECT account_id,party_reference,display_name,account_status,created_at,version FROM advertising_publisher_account WHERE tenant_id=? ORDER BY created_at DESC,account_id ASC",
            "SELECT COUNT(*) FROM advertising_publisher_account WHERE tenant_id=?",1,
            s -> s.setString(1,tenant.value()),
            r -> new PublisherAccount(new AdvertisingAccountId(r.getObject(1,UUID.class)),tenant,
                new PartyReference(r.getString(2)),r.getString(3),AccountStatus.valueOf(r.getString(4)),
                r.getTimestamp(5).toInstant(),r.getLong(6))));
    }

    @Override public CompletionStage<AdvertisingPage<PublisherProperty>> listProperties(TenantId tenant, AdvertisingAccountId publisherId, AdvertisingPageRequest page) {
        return execute(() -> queryPage(tenant,page,
            "SELECT property_id,publisher_account_id,property_name,canonical_uri,property_status,created_at,version FROM advertising_publisher_property WHERE tenant_id=? AND publisher_account_id=? ORDER BY created_at DESC,property_id ASC",
            "SELECT COUNT(*) FROM advertising_publisher_property WHERE tenant_id=? AND publisher_account_id=?",2,
            s -> { s.setString(1,tenant.value()); s.setObject(2,publisherId.value()); },
            r -> new PublisherProperty(new PublisherPropertyId(r.getObject(1,UUID.class)),tenant,
                new AdvertisingAccountId(r.getObject(2,UUID.class)),r.getString(3),URI.create(r.getString(4)),
                PropertyStatus.valueOf(r.getString(5)),r.getTimestamp(6).toInstant(),r.getLong(7))));
    }

    @Override public CompletionStage<AdvertisingPage<AdPlacement>> listPlacements(TenantId tenant, PublisherPropertyId propertyId, AdvertisingPageRequest page) {
        return execute(() -> queryPage(tenant,page,
            "SELECT placement_id,property_id,placement_code,format,placement_status,created_at,version FROM advertising_ad_placement WHERE tenant_id=? AND property_id=? ORDER BY created_at DESC,placement_id ASC",
            "SELECT COUNT(*) FROM advertising_ad_placement WHERE tenant_id=? AND property_id=?",2,
            s -> { s.setString(1,tenant.value()); s.setObject(2,propertyId.value()); },
            r -> new AdPlacement(new AdPlacementId(r.getObject(1,UUID.class)),tenant,
                new PublisherPropertyId(r.getObject(2,UUID.class)),r.getString(3),r.getString(4),
                PlacementStatus.valueOf(r.getString(5)),r.getTimestamp(6).toInstant(),r.getLong(7))));
    }

    private <T> AdvertisingPage<T> queryPage(TenantId tenant, AdvertisingPageRequest page,
            String selectSql, String countSql, int filterParameterCount, SqlBinder binder, RowMapper<T> mapper) throws SQLException {
        try (Connection c=dataSource.getConnection(); PreparedStatement count=c.prepareStatement(countSql)) {
            binder.bind(count);
            long total;
            try (ResultSet rows=count.executeQuery()) { rows.next(); total=rows.getLong(1); }
            List<T> items=new ArrayList<>();
            try (PreparedStatement select=c.prepareStatement(selectSql)) {
                binder.bind(select);
                select.setInt(filterParameterCount+1,page.size());
                select.setInt(filterParameterCount+2,page.offset());
                try (ResultSet rows=select.executeQuery()) {
                    while(rows.next()) items.add(mapper.map(rows));
                }
            }
            return new AdvertisingPage<>(items,total,page.page(),page.size());
        }
    }

    @FunctionalInterface private interface SqlBinder { void bind(PreparedStatement statement) throws SQLException; }
    @FunctionalInterface private interface RowMapper<T> { T map(ResultSet result) throws SQLException; }

    private static void requireOne(int updated,String operation) {
        if(updated!=1) throw new IllegalStateException(operation+" failed: record missing, duplicate, or stale version");
    }
    private static <T> CompletionStage<T> execute(SqlSupplier<T> work) {
        try { return CompletableFuture.completedFuture(work.get()); }
        catch(SQLException e) { return CompletableFuture.failedFuture(new IllegalStateException("Advertising PostgreSQL persistence failed",e)); }
        catch(RuntimeException e) { return CompletableFuture.failedFuture(e); }
    }
    @FunctionalInterface private interface SqlSupplier<T> { T get() throws SQLException; }
}
