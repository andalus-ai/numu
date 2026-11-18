package tech.kayys.numou.content.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.model.ContentTypeId;
import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

/** Runs when CONTENT_TEST_JDBC_URL points at an isolated PostgreSQL database. */
class PostgresContentRepositoryIntegrationTest {
    private static DataSource dataSource;
    private static PostgresContentRepository repository;

    @BeforeAll static void migrateDatabase() throws Exception {
        String url=System.getenv("CONTENT_TEST_JDBC_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(url!=null&&!url.isBlank(),
                "Set CONTENT_TEST_JDBC_URL to enable PostgreSQL integration tests");
        String user=System.getenv().getOrDefault("CONTENT_TEST_DB_USER","postgres");
        String password=System.getenv().getOrDefault("CONTENT_TEST_DB_PASSWORD","postgres");
        dataSource=new DriverManagerDataSource(url,user,password);
        repository=new PostgresContentRepository(dataSource);
        for(String migration:new String[]{"V1__content_core.sql","V2__content_type_schemas.sql","V3__content_lifecycle.sql","V4__bind_content_revisions_to_schema.sql"}) {
            try(InputStream in=getClassResource(migration)) {
                String sql=new String(Objects.requireNonNull(in,"Missing migration resource: "+migration).readAllBytes(),StandardCharsets.UTF_8);
                try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){s.execute(sql);}
            }
        }
    }

    @Test void persistsRevisionSchemaIdentityAndOptimisticAppendAtomically() throws Exception {
        TenantId tenant=new TenantId(UUID.randomUUID());ActorId actor=new ActorId(UUID.randomUUID());
        ContentTypeId type=seedPublishedSchema(tenant,1);
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        ContentEntry first=ContentEntry.create(tenant,"First","first","{}",type,1,actor,now);
        repository.create(first);
        ContentEntry loaded=repository.find(tenant,first.id()).orElseThrow();
        assertEquals(type,loaded.currentRevision().contentTypeId());
        assertEquals(1,loaded.currentRevision().schemaVersion());

        seedSchemaVersion(tenant,type,2);
        ContentEntry next=loaded.revise(1,"Second","second","{v:2}",type,2,actor,now.plusSeconds(1));
        assertTrue(repository.appendRevision(next,1));
        ContentEntry roundTrip=repository.find(tenant,first.id()).orElseThrow();
        assertEquals(2,roundTrip.currentRevisionNumber());
        assertEquals(1,roundTrip.revisions().get(0).schemaVersion());
        assertEquals(2,roundTrip.revisions().get(1).schemaVersion());
        assertFalse(repository.appendRevision(next,1),"A stale revision must not be appended twice");
    }

    @Test void tenantScopedSchemaForeignKeyRejectsUnknownSchemaAndRollsBackEntry() {
        TenantId tenant=new TenantId(UUID.randomUUID());ActorId actor=new ActorId(UUID.randomUUID());
        ContentEntry invalid=ContentEntry.create(tenant,"Invalid","invalid","{}",ContentTypeId.newId(),1,actor,Instant.now());
        assertThrows(IllegalStateException.class,()->repository.create(invalid));
        assertTrue(repository.find(tenant,invalid.id()).isEmpty(),"Header insert must roll back when revision schema FK fails");
    }

    private static ContentTypeId seedPublishedSchema(TenantId tenant,int version) throws SQLException {
        ContentTypeId type=ContentTypeId.newId();
        try(Connection c=dataSource.getConnection()) {
            try(PreparedStatement p=c.prepareStatement("INSERT INTO content_type(tenant_id,content_type_id,type_key,display_name,created_at) VALUES (?,?,?,?,?)")) {
                p.setObject(1,tenant.value());p.setObject(2,type.value());p.setString(3,"article");p.setString(4,"Article");p.setTimestamp(5,Timestamp.from(Instant.now()));p.executeUpdate();
            }
        }
        seedSchemaVersion(tenant,type,version);
        return type;
    }

    private static void seedSchemaVersion(TenantId tenant,ContentTypeId type,int version) throws SQLException {
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(
                "INSERT INTO content_type_schema(tenant_id,content_type_id,schema_version,status,created_at,published_at) VALUES (?,?,?,'PUBLISHED',?,?)")) {
            Timestamp now=Timestamp.from(Instant.now());p.setObject(1,tenant.value());p.setObject(2,type.value());
            p.setInt(3,version);p.setTimestamp(4,now);p.setTimestamp(5,now);p.executeUpdate();
        }
    }

    private static InputStream getClassResource(String name) {
        return PostgresContentRepositoryIntegrationTest.class.getResourceAsStream("/db/migration/"+name);
    }

    private record DriverManagerDataSource(String url,String user,String password) implements DataSource {
        public Connection getConnection() throws SQLException {return DriverManager.getConnection(url,user,password);}
        public Connection getConnection(String username,String pwd) throws SQLException {return DriverManager.getConnection(url,username,pwd);}
        public java.io.PrintWriter getLogWriter(){return DriverManager.getLogWriter();}
        public void setLogWriter(java.io.PrintWriter out){DriverManager.setLogWriter(out);}
        public void setLoginTimeout(int seconds){DriverManager.setLoginTimeout(seconds);}
        public int getLoginTimeout(){return DriverManager.getLoginTimeout();}
        public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getLogger("java.sql");}
        public <T>T unwrap(Class<T> iface) throws SQLException {if(iface.isInstance(this))return iface.cast(this);throw new SQLException("Not a wrapper");}
        public boolean isWrapperFor(Class<?> iface){return iface.isInstance(this);}
    }
}
