package tech.kayys.numou.developer.apicatalog.application.command;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;
public record RetireApiVersionCommand(String productId,String version) implements Command {
 public RetireApiVersionCommand { Objects.requireNonNull(productId,"productId cannot be null");Objects.requireNonNull(version,"version cannot be null"); }
}