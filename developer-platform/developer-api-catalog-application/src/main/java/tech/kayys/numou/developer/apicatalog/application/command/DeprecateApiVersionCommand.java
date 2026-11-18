package tech.kayys.numou.developer.apicatalog.application.command;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;
public record DeprecateApiVersionCommand(String productId,String version) implements Command {
 public DeprecateApiVersionCommand { Objects.requireNonNull(productId,"productId cannot be null");Objects.requireNonNull(version,"version cannot be null"); }
}