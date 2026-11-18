package tech.kayys.numou.developer.apicatalog.application.query;
import tech.kayys.numou.foundation.application.page.PageRequest;
import tech.kayys.numou.foundation.application.query.Query;
import java.util.Objects;
public record ListApiProductsQuery(PageRequest pageRequest) implements Query {
 public ListApiProductsQuery { Objects.requireNonNull(pageRequest,"pageRequest cannot be null"); }
}