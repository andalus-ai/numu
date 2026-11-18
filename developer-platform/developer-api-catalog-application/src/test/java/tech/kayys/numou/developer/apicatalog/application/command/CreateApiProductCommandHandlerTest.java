package tech.kayys.numou.developer.apicatalog.application.command;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.developer.apicatalog.application.port.*;
import tech.kayys.numou.developer.apicatalog.domain.*;
import tech.kayys.numou.foundation.application.context.*;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.page.*;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
class CreateApiProductCommandHandlerTest {
 @Test void createsProductUsingAuthenticatedTenantAndPublishesCreationEvent() {
  var tenant=TenantId.generate();var context=new ExecutionContext(tenant,null,null,ActorId.anonymous(),null,null,Instant.parse("2026-11-04T10:00:00Z"),null,null,null);
  var saved=new ArrayList<ApiProduct>();var published=new ArrayList<tech.kayys.numou.foundation.domain.event.DomainEvent>();
  ApiProductRepository repository=new ApiProductRepository(){
   public Uni<Optional<ApiProduct>> findById(TenantId t,ApiProductId id){return Uni.createFrom().item(Optional.empty());}
   public Uni<Optional<ApiProduct>> findBySlug(TenantId t,String slug){assertEquals(tenant,t);return Uni.createFrom().item(Optional.empty());}
   public Uni<Page<ApiProduct>> findAll(TenantId t,PageRequest p){return Uni.createFrom().item(Page.empty(p));}
   public Uni<Void> save(TenantId t,ApiProduct p,long expected){assertEquals(tenant,t);assertEquals(0,expected);saved.add(p);return Uni.createFrom().voidItem();}
  };
  ApiCatalogAuthorizationPort authorization=new ApiCatalogAuthorizationPort(){
   public Uni<Void> requireCanManage(ExecutionContext c,TenantId t){assertEquals(tenant,t);return Uni.createFrom().voidItem();}
   public Uni<Void> requireCanRead(ExecutionContext c,TenantId t){return Uni.createFrom().voidItem();}
  };
  UnitOfWork uow=new UnitOfWork(){public <R> Uni<R> execute(Supplier<Uni<R>> work){return work.get();}};
  EventPublisher publisher=events->{published.addAll(events);return Uni.createFrom().voidItem();};
  var handler=new CreateApiProductCommandHandler(repository,authorization,()->context,uow,publisher);
  var id=handler.handle(new CreateApiProductCommand("Orders API","orders","Order lifecycle")).await().indefinitely();
  assertEquals(1,saved.size());assertEquals(id,saved.getFirst().id());assertEquals(tenant.value(),saved.getFirst().providerTenantId());
  assertEquals("developer.api-product.created",published.getFirst().eventType());
 }
}