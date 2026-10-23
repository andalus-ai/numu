package tech.kayys.numou.warehouse.domain;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class WarehouseWorkOrderTest {
 @Test void completionRequiresAllPlannedQuantity(){WarehouseWorkOrder w=WarehouseWorkOrder.create("t","R-1",null,WarehouseWorkType.RECEIVING,List.of(WarehouseWorkLine.planned("l","item","EA",new BigDecimal("2")))).start();assertThrows(IllegalStateException.class,w::complete);assertEquals(WarehouseWorkStatus.COMPLETED,w.execute("l",new BigDecimal("2"),null).complete().status());}
 @Test void overExecutionAndExecutionBeforeStartAreRejected(){WarehouseWorkOrder w=WarehouseWorkOrder.create("t","P-1",null,WarehouseWorkType.PICKING,List.of(WarehouseWorkLine.planned("l","item","EA",BigDecimal.ONE)));assertThrows(IllegalStateException.class,()->w.execute("l",BigDecimal.ONE,"A-1"));WarehouseWorkOrder active=w.start();assertThrows(IllegalArgumentException.class,()->active.execute("l",new BigDecimal("1.01"),"A-1"));}
 @Test void duplicateLineIdsAreRejected(){WarehouseWorkLine l=WarehouseWorkLine.planned("l","i","EA",BigDecimal.ONE);assertThrows(IllegalArgumentException.class,()->WarehouseWorkOrder.create("t","R",null,WarehouseWorkType.RECEIVING,List.of(l,l)));}
}