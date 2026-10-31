package tech.kayys.numou.organization.application.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.application.port.PartyOrganizationEligibilityPort;
import tech.kayys.numou.organization.domain.*;
import tech.kayys.numou.organization.spi.OrganizationPartyAssociationRepository;
import tech.kayys.numou.organization.spi.OrganizationRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OrganizationPartyAssociationHandlerTest {
 private static final Instant NOW=Instant.parse("2026-10-31T00:00:00Z");
 private TenantId tenant; private OrganizationId organizationId; private PartyId partyId;
 private final FakeOrganizations organizations=new FakeOrganizations();
 private final FakeAssociations associations=new FakeAssociations();
 private final FakeParties parties=new FakeParties();

 @BeforeEach void setUp() {
  tenant=TenantId.generate(); organizationId=OrganizationId.generate(); partyId=PartyId.generate();
  organizations.rows.clear(); associations.rows.clear(); parties.lookups.set(0);
  parties.eligibility=new PartyOrganizationEligibilityPort.Eligibility(true,true,true);
  organizations.save(Organization.create(organizationId,tenant,"Example","Example Ltd","REG-1"))
   .toCompletableFuture().join();
 }

 @Test void rejectsTenantMismatchBeforePartyLookup() {
  var result=propose(TenantId.generate());
  assertTrue(result.isFailure());
  assertEquals("ORGANIZATION_TENANT_MISMATCH",result.failureError().orElseThrow().code());
  assertEquals(0,parties.lookups.get());
  assertTrue(associations.rows.isEmpty());
 }

 @Test void rejectsIneligiblePartyWithoutPersisting() {
  parties.eligibility=new PartyOrganizationEligibilityPort.Eligibility(true,true,false);
  var result=propose(tenant);
  assertTrue(result.isFailure());
  assertEquals("PARTY_NOT_ELIGIBLE_FOR_ORGANIZATION",result.failureError().orElseThrow().code());
  assertTrue(associations.rows.isEmpty());
 }

 @Test void proposalIsPersistedAndRetryIsIdempotent() {
  var handler=new ProposeOrganizationPartyAssociationHandler(organizations,associations,parties);
  var command=command(tenant);
  var first=handler.handle(command).toCompletableFuture().join();
  var retry=handler.handle(command).toCompletableFuture().join();
  assertTrue(first.isSuccess()); assertEquals(first.orElseThrow(),retry.orElseThrow());
  assertEquals(1,associations.rows.size());
  assertEquals(OrganizationPartyAssociationStatus.PENDING_VERIFICATION,first.orElseThrow().status());
 }

 @Test void activationRechecksPartyEligibility() {
  var pending=propose(tenant).orElseThrow();
  parties.eligibility=new PartyOrganizationEligibilityPort.Eligibility(true,false,true);
  var result=new ActivateOrganizationPartyAssociationHandler(organizations,associations,parties)
   .handle(new ActivateOrganizationPartyAssociationCommand(pending.id(),tenant,NOW.plusSeconds(5),"verifier"))
   .toCompletableFuture().join();
  assertTrue(result.isFailure());
  assertEquals(OrganizationPartyAssociationStatus.PENDING_VERIFICATION,associations.rows.get(pending.id()).status());
 }

 @Test void activationPersistsActiveState() {
  var pending=propose(tenant).orElseThrow();
  var result=new ActivateOrganizationPartyAssociationHandler(organizations,associations,parties)
   .handle(new ActivateOrganizationPartyAssociationCommand(pending.id(),tenant,NOW.plusSeconds(5),"verifier"))
   .toCompletableFuture().join();
  assertTrue(result.isSuccess());
  assertEquals(OrganizationPartyAssociationStatus.ACTIVE,result.orElseThrow().status());
 }

 private Result<OrganizationPartyAssociation> propose(TenantId tenantId) {
  return new ProposeOrganizationPartyAssociationHandler(organizations,associations,parties)
   .handle(command(tenantId)).toCompletableFuture().join();
 }
 private ProposeOrganizationPartyAssociationCommand command(TenantId tenantId) {
  return new ProposeOrganizationPartyAssociationCommand(UUID.randomUUID(),tenantId,organizationId,partyId,
   NOW,"operator","registry:123");
 }

 private static final class FakeParties implements PartyOrganizationEligibilityPort {
  final AtomicInteger lookups=new AtomicInteger();
  volatile Eligibility eligibility=new Eligibility(true,true,true);
  public CompletionStage<Eligibility> findEligibility(PartyId id) {
   lookups.incrementAndGet(); return CompletableFuture.completedFuture(eligibility);
  }
 }
 private static final class FakeOrganizations implements OrganizationRepository {
  final Map<OrganizationId,Organization> rows=new HashMap<>();
  public CompletionStage<Organization> save(Organization value) {
   rows.put(value.getId(),value); return CompletableFuture.completedFuture(value);
  }
  public CompletionStage<Optional<Organization>> findById(OrganizationId id) {
   return CompletableFuture.completedFuture(Optional.ofNullable(rows.get(id)));
  }
  public CompletionStage<Boolean> existsById(OrganizationId id) {
   return CompletableFuture.completedFuture(rows.containsKey(id));
  }
  public CompletionStage<Void> delete(Organization value) {
   rows.remove(value.getId()); return CompletableFuture.completedFuture(null);
  }
  public CompletionStage<Void> deleteById(OrganizationId id) {
   rows.remove(id); return CompletableFuture.completedFuture(null);
  }
 }
 private static final class FakeAssociations implements OrganizationPartyAssociationRepository {
  final Map<UUID,OrganizationPartyAssociation> rows=new HashMap<>();
  public CompletionStage<OrganizationPartyAssociation> save(OrganizationPartyAssociation value) {
   rows.put(value.id(),value); return CompletableFuture.completedFuture(value);
  }
  public CompletionStage<Optional<OrganizationPartyAssociation>> findById(UUID id) {
   return CompletableFuture.completedFuture(Optional.ofNullable(rows.get(id)));
  }
  public CompletionStage<Optional<OrganizationPartyAssociation>> findCurrentByOrganizationId(OrganizationId id) {
   return CompletableFuture.completedFuture(rows.values().stream()
    .filter(a->a.organizationId().equals(id)&&a.status().reservesIdentity()).findFirst());
  }
  public CompletionStage<Optional<OrganizationPartyAssociation>> findCurrentByPartyId(PartyId id) {
   return CompletableFuture.completedFuture(rows.values().stream()
    .filter(a->a.partyId().equals(id)&&a.status().reservesIdentity()).findFirst());
  }
  public CompletionStage<List<OrganizationPartyAssociationHistory>> findHistory(UUID id) {
   return CompletableFuture.completedFuture(List.of());
  }
 }
}
