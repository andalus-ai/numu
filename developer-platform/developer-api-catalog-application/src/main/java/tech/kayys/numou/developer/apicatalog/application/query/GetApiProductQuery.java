package tech.kayys.numou.developer.apicatalog.application.query;
import tech.kayys.numou.foundation.application.query.Query;
import java.util.Objects;
public record GetApiProductQuery(String productId) implements Query {
 public GetApiProductQuery { Objects.requireNonNull(productId,"productId cannot be null"); }
}