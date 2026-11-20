package tech.kayys.numou.developer.apicatalog.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One worker-confined JDBC transaction shared by API catalog persistence and outbox writes.
 * Nested repository/publisher calls reuse the same connection; tenant changes inside one
 * transaction are rejected.
 */
@ApplicationScoped
public final class ApiCatalogJdbcTransactionManager implements UnitOfWork {
 private final DataSource dataSource;
 private final ThreadLocal<Transaction> current=new ThreadLocal<>();
 @Inject public ApiCatalogJdbcTransactionManager(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource);}

 @Override public <R> Uni<R> execute(Supplier<Uni<R>> work){
  Objects.requireNonNull(work);
  return Uni.createFrom().deferred(()->{
   if(current.get()!=null)return Objects.requireNonNull(work.get());
   return Uni.createFrom().item(this::begin).flatMap(tx->{
    current.set(tx);
    final Uni<R> operation;
    try{operation=Objects.requireNonNull(work.get());}
    catch(Throwable failure){rollback(tx,failure);cleanup(tx);return Uni.createFrom().failure(failure);}
    return operation.invoke(ignored->commit(tx))
      .onFailure().invoke(failure->rollback(tx,failure))
      .eventually(()->{cleanup(tx);return Uni.createFrom().voidItem();});
   });
  }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
 }

 <T> Uni<T> withTenant(TenantId tenant,SqlWork<T> work){
  Objects.requireNonNull(tenant);Objects.requireNonNull(work);
  Transaction active=current.get();
  if(active!=null)return Uni.createFrom().item(()->apply(active,tenant,work));
  return Uni.createFrom().item(()->{
   try(Connection connection=dataSource.getConnection()){
    connection.setAutoCommit(false);
    connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    try{installTenant(connection,tenant.value());T result=work.apply(connection);connection.commit();return result;}
    catch(SQLException|RuntimeException|Error failure){try{connection.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;}
   }catch(SQLException failure){throw translate(failure);}
  }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
 }

 private <T>T apply(Transaction tx,TenantId tenant,SqlWork<T> work){
  try{
   if(tx.tenantId!=null&&!tx.tenantId.equals(tenant.value()))throw new IllegalStateException("A developer API catalog transaction cannot cross provider tenants");
   if(tx.tenantId==null)tx.tenantId=tenant.value();
   installTenant(tx.connection,tenant.value());
   return work.apply(tx.connection);
  }catch(SQLException failure){throw translate(failure);}
 }
 private Transaction begin(){
  try{Connection c=dataSource.getConnection();c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);return new Transaction(c);}
  catch(SQLException failure){throw translate(failure);}
 }
 private static void installTenant(Connection c,UUID tenant)throws SQLException{
  try(PreparedStatement s=c.prepareStatement("SELECT set_config('numou.tenant_id',?,true)")){s.setString(1,tenant.toString());s.execute();}
 }
 private static void commit(Transaction tx){try{tx.connection.commit();}catch(SQLException e){throw translate(e);}}
 private static void rollback(Transaction tx,Throwable original){try{tx.connection.rollback();}catch(SQLException e){original.addSuppressed(e);}}
 private void cleanup(Transaction tx){current.remove();try{tx.connection.close();}catch(SQLException e){throw new IllegalStateException("Unable to close API catalog transaction",e);}}
 private static RuntimeException translate(SQLException e){
  if("40001".equals(e.getSQLState()))return new PostgresApiProductRepository.OptimisticLockException("Concurrent API catalog update; reload and retry",e);
  return new IllegalStateException("API catalog PostgreSQL transaction failed",e);
 }
 @FunctionalInterface interface SqlWork<T>{T apply(Connection connection)throws SQLException;}
 private static final class Transaction{final Connection connection;UUID tenantId;Transaction(Connection c){connection=c;}}
}
