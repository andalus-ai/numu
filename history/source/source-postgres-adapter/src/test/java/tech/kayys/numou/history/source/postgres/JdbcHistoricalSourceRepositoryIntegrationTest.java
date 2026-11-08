package tech.kayys.numou.history.source.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.history.source.domain.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHistoricalSourceRepositoryIntegrationTest {
 @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
   .withDatabaseName("history").withUsername("admin").withPassword("admin-password");
 static JdbcHistoricalSourceRepository repository;
 static PGSimpleDataSource appDataSource;
 @BeforeAll static void start() throws Exception {
  Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
    .locations("classpath:db/migration").load().migrate();
  try(var c=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
      var s=c.createStatement()) {
   s.execute("CREATE ROLE history_app LOGIN PASSWORD 'history-password' NOSUPERUSER NOBYPASSRLS");
   s.execute("GRANT USAGE ON SCHEMA public TO history_app");
   s.execute("GRANT SELECT, INSERT, UPDATE ON historical_source TO history_app");
   s.execute("GRANT SELECT, INSERT ON historical_source_revision, historical_claim, historical_claim_evidence, historical_source_assessment, historical_source_assessment_review TO history_app");
  }
  appDataSource=new PGSimpleDataSource(); appDataSource.setURL(POSTGRES.getJdbcUrl());
  appDataSource.setUser("history_app"); appDataSource.setPassword("history-password");
  repository=new JdbcHistoricalSourceRepository(appDataSource);
 }
 @Test void persistsRevisionsClaimsAndEvidenceWithTenantIsolation() {
  UUID tenant=UUID.randomUUID(), otherTenant=UUID.randomUUID(); Instant now=Instant.parse("2026-11-07T00:00:00Z");
  var source=HistoricalSource.register(HistoricalSourceId.generate(),HistoricalSourceKind.ARCHIVAL_RECORD,
   "Archive record",null,"Original record","https://archive.example/record/7","National Archive","1945","id-ID",now);
  assertTrue(repository.register(tenant,source));
  assertEquals(source,repository.find(tenant,source.id()).orElseThrow());
  assertTrue(repository.find(otherTenant,source.id()).isEmpty());
  var revised=source.revise(HistoricalSourceKind.ARCHIVAL_RECORD,"Archive record (catalogued)",null,
   "Original record",source.canonicalUri(),"National Archive","1945","id-ID",now.plusSeconds(1));
  assertTrue(repository.revise(tenant,revised,1));
  assertFalse(repository.revise(tenant,revised,1));
  assertEquals(2,repository.find(tenant,source.id()).orElseThrow().revision());
  var archived=revised.archive(now.plusSeconds(3));
  assertTrue(repository.revise(tenant,archived,2));
  assertEquals(HistoricalSourceStatus.ARCHIVED,repository.find(tenant,source.id()).orElseThrow().status());
  var claim=HistoricalClaim.create(HistoricalClaimId.generate(),"EVENT","event-45","An event occurred.",now);
  assertTrue(repository.createClaim(tenant,claim));
  var evidence=ClaimEvidence.create(claim.id(),source.id(),EvidenceRelation.SUPPORTS,
   EvidenceLocatorType.PAGE,"page 17","Quoted archival passage",now.plusSeconds(2));
  assertTrue(repository.appendEvidence(tenant,evidence));
  assertEquals(1,repository.listEvidence(tenant,claim.id(),20).size());
  assertTrue(repository.listEvidence(otherTenant,claim.id(),20).isEmpty());
  var assessment=SourceAssessment.create(source.id(),SourceAssessmentDimension.RELIABILITY,
   SourceAssessmentConclusion.UNCERTAIN,"The account is not independently corroborated.","numou.history.source-criticism","1.0","actor:historian-1",now.plusSeconds(4));
  assertTrue(repository.appendAssessment(tenant,assessment));
  assertEquals(assessment,repository.findAssessment(tenant,assessment.id()).orElseThrow());
  assertEquals(1,repository.listAssessments(tenant,source.id(),20).size());
  var review=SourceAssessmentReview.create(assessment.id(),SourceAssessmentReviewDecision.REQUEST_CHANGES,
   "Provide a second witness.","actor:editor-1",now.plusSeconds(5));
  assertTrue(repository.appendAssessmentReview(tenant,review));
  assertEquals(review,repository.listAssessmentReviews(tenant,assessment.id(),20).get(0));
  assertTrue(repository.listAssessments(otherTenant,source.id(),20).isEmpty());
  assertTrue(repository.listAssessmentReviews(otherTenant,assessment.id(),20).isEmpty());
 }
 @Test void duplicateSourceRegistrationReturnsConflictInsteadOfOverwriting() {
  UUID tenant=UUID.randomUUID(); Instant now=Instant.parse("2026-11-07T00:00:00Z");
  var source=HistoricalSource.register(HistoricalSourceId.generate(),HistoricalSourceKind.BOOK,"Book",null,null,null,null,null,null,now);
  assertTrue(repository.register(tenant,source)); assertFalse(repository.register(tenant,source));
 }
}