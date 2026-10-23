package tech.kayys.numou.warehouse.application;
import java.math.BigDecimal;
/** Port to the inventory owner. Implementations must use inventory's atomic, idempotent commands. */
public interface InventoryWarehouseGateway {
 void receive(String tenantId,String itemId,String warehouseId,BigDecimal quantity,BigDecimal unitCost,String currency,String reference,String commandKey,String workOrderId,String lineId);
 void issueReserved(String tenantId,String reservationId,String itemId,String warehouseId,BigDecimal quantity,String currency,String reference,String commandKey,String workOrderId,String lineId);
}