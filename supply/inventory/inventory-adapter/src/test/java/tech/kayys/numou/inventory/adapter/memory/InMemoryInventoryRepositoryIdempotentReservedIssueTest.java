package tech.kayys.numou.inventory.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.*;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryInventoryRepositoryIdempotentReservedIssueTest {
 @Test void retryReturnsOriginalMovementAndDoesNotIssueTwice() {
  InMemoryInventoryRepository repository = new InMemoryInventoryRepository();
  ItemId item = ItemId.of("item-1");
  WarehouseId warehouse = WarehouseId.of("warehouse-1");
  repository.saveMovement(new InventoryMovement(MovementId.generate(), item, warehouse, MovementType.RECEIPT,
    new BigDecimal("5"), new BigDecimal("2.00"), "USD", "opening", Instant.now()));
  InventoryReservation reservation = repository.reserveIfAvailable(InventoryReservation.create(
    "tenant-a", item, warehouse, new BigDecimal("2"), "EA", "pick-1", null));
  InventoryCommandKey key = new InventoryCommandKey("warehouse:work-1:line-1:issue-reserved", "command-1");
  InventoryMovement firstRequest = issue(item, warehouse, "pick-line-1");
  InventoryMovement first = repository.postReservedIssue(key, reservation.id(), firstRequest, new BigDecimal("2"));
  InventoryMovement retry = repository.postReservedIssue(key, reservation.id(), issue(item, warehouse, "pick-line-1"), new BigDecimal("2"));
  assertEquals(first.id(), retry.id());
  assertEquals(new BigDecimal("3"), repository.findByItemAndWarehouse(item, warehouse).stream()
    .map(InventoryMovement::quantity).reduce(BigDecimal.ZERO, BigDecimal::add));
  assertEquals(ReservationStatus.COMMITTED, repository.findReservation(reservation.id()).status());
 }
 @Test void sameKeyCannotBeReusedForDifferentReservation() {
  InMemoryInventoryRepository repository = new InMemoryInventoryRepository();
  ItemId item = ItemId.of("item-1"); WarehouseId warehouse = WarehouseId.of("warehouse-1");
  repository.saveMovement(new InventoryMovement(MovementId.generate(), item, warehouse, MovementType.RECEIPT,
    new BigDecimal("5"), BigDecimal.ONE, "USD", "opening", Instant.now()));
  InventoryReservation one = repository.reserveIfAvailable(InventoryReservation.create("tenant-a",item,warehouse,BigDecimal.ONE,"EA","one",null));
  InventoryReservation two = repository.reserveIfAvailable(InventoryReservation.create("tenant-a",item,warehouse,BigDecimal.ONE,"EA","two",null));
  InventoryCommandKey key = new InventoryCommandKey("warehouse:work-1:line-1:issue-reserved", "command-1");
  repository.postReservedIssue(key,one.id(),issue(item,warehouse,"same-ref"),BigDecimal.ONE);
  assertThrows(IllegalArgumentException.class,()->repository.postReservedIssue(key,two.id(),issue(item,warehouse,"same-ref"),BigDecimal.ONE));
 }
 private static InventoryMovement issue(ItemId item,WarehouseId warehouse,String ref) {
  return new InventoryMovement(MovementId.generate(),item,warehouse,MovementType.ISSUE,BigDecimal.ONE.negate(),
    BigDecimal.ONE,"USD",ref,Instant.now());
 }
}