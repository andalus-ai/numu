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
        for(String migration:new String[]{"V1__content_core.sql","V2__content_type_schemas.sql","V3__content_lifecycle.sql","V4__bind_content_revisions_to_schema.sql","V5__content_database_invariant_hardening.sql","V6__content_editorial_workflow.sql"}) {
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


    @Test void publishedSchemaAndItsFieldsAreImmutableAtTheDatabaseBoundary() throws Exception {
        TenantId tenant=new TenantId(UUID.randomUUID());
        ContentTypeId type=seedPublishedSchema(tenant,1);
        assertThrows(SQLException.class,()->execute(
                "INSERT INTO content_type_field(tenant_id,content_type_id,schema_version,field_name,field_type,required,nullable) VALUES ('"+
                        tenant.value()+"','"+type.value()+"',1,'headline','STRING',true,false)"));
        assertThrows(SQLException.class,()->execute(
                "UPDATE content_type_schema SET published_at=published_at + INTERVAL '1 second' WHERE tenant_id='"+
                        tenant.value()+"' AND content_type_id='"+type.value()+"' AND schema_version=1"));
        assertThrows(SQLException.class,()->execute(
                "DELETE FROM content_type_schema WHERE tenant_id='"+tenant.value()+"' AND content_type_id='"+
                        type.value()+"' AND schema_version=1"));
    }

    @Test void revisionHistoryIsAppendOnlyAndCurrentPointerMustReferenceAnExistingRevision() throws Exception {
        TenantId tenant=new TenantId(UUID.randomUUID());ActorId actor=new ActorId(UUID.randomUUID());
        ContentTypeId type=seedPublishedSchema(tenant,1);
        ContentEntry entry=ContentEntry.create(tenant,"Original","original","{}",type,1,actor,Instant.now());
        repository.create(entry);

        assertThrows(SQLException.class,()->execute("UPDATE content_revision SET title='tampered' WHERE tenant_id='"+
                tenant.value()+"' AND content_id='"+entry.id().value()+"' AND revision_number=1"));
        assertThrows(SQLException.class,()->execute("DELETE FROM content_revision WHERE tenant_id='"+
                tenant.value()+"' AND content_id='"+entry.id().value()+"' AND revision_number=1"));
        assertThrows(SQLException.class,()->execute("UPDATE content_entry SET current_revision=3 WHERE tenant_id='"+
                tenant.value()+"' AND content_id='"+entry.id().value()+"'"));

        try(Connection c=dataSource.getConnection();Statement s=c.createStatement()) {
            c.setAutoCommit(false);
            s.executeUpdate("UPDATE content_entry SET current_revision=2 WHERE tenant_id='"+tenant.value()+
                    "' AND content_id='"+entry.id().value()+"'");
            assertThrows(SQLException.class,c::commit,
                    "Deferred head-to-revision FK must reject a pointer without a matching revision");
        }
        assertEquals(1,repository.find(tenant,entry.id()).orElseThrow().currentRevisionNumber());
    }

    @Test void databaseRejectsIllegalLifecycleTransitionsButAllowsTheEditorialPath() throws Exception {
        TenantId tenant=new TenantId(UUID.randomUUID());ActorId actor=new ActorId(UUID.randomUUID());
        ContentTypeId type=seedPublishedSchema(tenant,1);
        ContentEntry entry=ContentEntry.create(tenant,"Lifecycle","lifecycle","{}",type,1,actor,Instant.now());
        repository.create(entry);
        assertThrows(SQLException.class,()->execute("UPDATE content_entry SET status='PUBLISHED' WHERE tenant_id='"+
                tenant.value()+"' AND content_id='"+entry.id().value()+"'"));
        execute("UPDATE content_entry SET status='IN_REVIEW' WHERE tenant_id='"+tenant.value()+"' AND content_id='"+entry.id().value()+"'");
        execute("UPDATE content_entry SET status='APPROVED' WHERE tenant_id='"+tenant.value()+"' AND content_id='"+entry.id().value()+"'");
        execute("UPDATE content_entry SET status='PUBLISHED' WHERE tenant_id='"+tenant.value()+"' AND content_id='"+entry.id().value()+"'");
        assertEquals(ContentStatus.PUBLISHED,repository.find(tenant,entry.id()).orElseThrow().status());
    }

    @Test void editorialSubmissionDecisionAndOutboxCommitAtomically() throws Exception {
        TenantId tenant=new TenantId(UUID.randomUUID());
        ActorId author=new ActorId(UUID.randomUUID());
        UUID reviewer=UUID.randomUUID();
        ContentTypeId type=seedPublishedSchema(tenant,1);
        ContentEntry entry=ContentEntry.create(tenant,"Review me","review-me","{}",type,1,author,Instant.now());
        repository.create(entry);

        PostgresEditorialReviewRepository editorial=new PostgresEditorialReviewRepository(dataSource);
        var policy=new tech.kayys.numou.content.domain.editorial.EditorialReviewPolicy(
                tech.kayys.numou.content.domain.editorial.EditorialReviewPolicy.Mode.SINGLE,
                java.util.List.of(reviewer),1);
        var submitted=tech.kayys.numou.content.domain.editorial.EditorialReview.submit(
                tenant,entry.id(),1,policy,Instant.now());
        editorial.submit(submitted);

        assertEquals(ContentStatus.IN_REVIEW,repository.find(tenant,entry.id()).orElseThrow().status());
        var reloaded=editorial.find(tenant,submitted.id()).orElseThrow();
        assertEquals(submitted.id(),reloaded.id());
        assertEquals(java.util.List.of(reviewer),reloaded.policy().reviewers());

        var approved=reloaded.decide(reviewer,
                tech.kayys.numou.content.domain.editorial.EditorialReview.Decision.APPROVE,null,Instant.now().plusSeconds(1));
        assertTrue(editorial.update(approved,reloaded.version()));
        assertEquals(tech.kayys.numou.content.domain.editorial.EditorialReview.Status.APPROVED,
                editorial.find(tenant,submitted.id()).orElseThrow().status());
        assertEquals(ContentStatus.APPROVED,repository.find(tenant,entry.id()).orElseThrow().status());

        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement(
                    "SELECT count(*) FROM content_editorial_outbox WHERE tenant_id=? AND review_id=?")) {
            statement.setObject(1,tenant.value());statement.setObject(2,submitted.id().value());
            try(ResultSet result=statement.executeQuery()){assertTrue(result.next());assertEquals(2,result.getInt(1));}
        }
        assertTrue(editorial.find(TenantId.of(UUID.randomUUID()),submitted.id()).isEmpty(),
                "Review lookup must remain tenant scoped");
    }

    @Test void editorialChangeRequestReturnsContentToDraftAndPreservesCommentHistory() throws Exception {
        TenantId tenant=new TenantId(UUID.randomUUID());
        ActorId author=new ActorId(UUID.randomUUID());
        UUID reviewer=UUID.randomUUID();
        ContentTypeId type=seedPublishedSchema(tenant,1);
        ContentEntry entry=ContentEntry.create(tenant,"Needs changes","needs-changes","{}",type,1,author,Instant.now());
        repository.create(entry);

        PostgresEditorialReviewRepository editorial=new PostgresEditorialReviewRepository(dataSource);
        var policy=new tech.kayys.numou.content.domain.editorial.EditorialReviewPolicy(
                tech.kayys.numou.content.domain.editorial.EditorialReviewPolicy.Mode.SINGLE,
                java.util.List.of(reviewer),1);
        var submitted=tech.kayys.numou.content.domain.editorial.EditorialReview.submit(
                tenant,entry.id(),1,policy,Instant.now());
        editorial.submit(submitted);
        var loaded=editorial.find(tenant,submitted.id()).orElseThrow();
        var commented=loaded.comment(new tech.kayys.numou.content.domain.editorial.EditorialReview.ReviewComment(
                UUID.randomUUID(),reviewer,"Please clarify the source",Instant.now()),Instant.now());
        assertTrue(editorial.update(commented,loaded.version()));
        var beforeDecision=editorial.find(tenant,submitted.id()).orElseThrow();
        var requested=beforeDecision.decide(reviewer,
                tech.kayys.numou.content.domain.editorial.EditorialReview.Decision.REQUEST_CHANGES,
                "Please clarify the source",Instant.now().plusSeconds(1));
        assertTrue(editorial.update(requested,beforeDecision.version()));
        var persisted=editorial.find(tenant,submitted.id()).orElseThrow();
        assertEquals(1,persisted.comments().size());
        assertEquals(ContentStatus.DRAFT,repository.find(tenant,entry.id()).orElseThrow().status());
    }

    @Test void editorialOutboxLeasesRetryAndWorkerOwnedAcknowledgement() throws Exception {
        TenantId tenant=new TenantId(UUID.randomUUID());
        ActorId author=new ActorId(UUID.randomUUID());
        ContentTypeId type=seedPublishedSchema(tenant,1);
        ContentEntry entry=ContentEntry.create(tenant,"Outbox","outbox","{}",type,1,author,Instant.now());
        repository.create(entry);
        UUID reviewer=UUID.randomUUID();
        var policy=new tech.kayys.numou.content.domain.editorial.EditorialReviewPolicy(
                tech.kayys.numou.content.domain.editorial.EditorialReviewPolicy.Mode.SINGLE,
                java.util.List.of(reviewer),1);
        Instant now=Instant.now();
        var review=tech.kayys.numou.content.domain.editorial.EditorialReview.submit(tenant,entry.id(),1,policy,now);
        new PostgresEditorialReviewRepository(dataSource).submit(review);

        PostgresEditorialOutboxRepository outbox=new PostgresEditorialOutboxRepository(dataSource);
        UUID worker=UUID.randomUUID();
        var claimed=outbox.claim(worker,now.plusSeconds(1),now.plusSeconds(121),10);
        assertEquals(1,claimed.size());
        assertEquals("REVIEW_SUBMITTED",claimed.get(0).eventType());
        assertFalse(outbox.markPublished(tenant,claimed.get(0).eventId(),UUID.randomUUID(),now.plusSeconds(2)),
                "A different worker must not acknowledge this lease");
        assertTrue(outbox.releaseForRetry(tenant,claimed.get(0).eventId(),worker,now.plusSeconds(10),"temporary failure"));

        var retried=outbox.claim(worker,now.plusSeconds(11),now.plusSeconds(131),10);
        assertEquals(1,retried.size());
        assertEquals(2,retried.get(0).attempts());
        assertTrue(outbox.markPublished(tenant,retried.get(0).eventId(),worker,now.plusSeconds(12)));
        assertTrue(outbox.claim(worker,now.plusSeconds(13),now.plusSeconds(133),10).isEmpty(),
                "Published rows must no longer be claimed");
    }

    private static void execute(String sql) throws SQLException {
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){s.execute(sql);}
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
        Timestamp now=Timestamp.from(Instant.now());
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try(PreparedStatement insert=c.prepareStatement(
                    "INSERT INTO content_type_schema(tenant_id,content_type_id,schema_version,status,created_at,published_at) VALUES (?,?,?,'DRAFT',?,NULL)")) {
                insert.setObject(1,tenant.value());insert.setObject(2,type.value());
                insert.setInt(3,version);insert.setTimestamp(4,now);insert.executeUpdate();
            }
            try(PreparedStatement publish=c.prepareStatement(
                    "UPDATE content_type_schema SET status='PUBLISHED',published_at=? WHERE tenant_id=? AND content_type_id=? AND schema_version=? AND status='DRAFT'")) {
                publish.setTimestamp(1,now);publish.setObject(2,tenant.value());publish.setObject(3,type.value());publish.setInt(4,version);
                assertEquals(1,publish.executeUpdate());
            }
            c.commit();
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
