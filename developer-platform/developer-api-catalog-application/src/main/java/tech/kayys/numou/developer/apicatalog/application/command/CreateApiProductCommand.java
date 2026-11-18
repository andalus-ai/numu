package tech.kayys.numou.developer.apicatalog.application.command;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;
public record CreateApiProductCommand(String name,String slug,String description) implements Command {
 public CreateApiProductCommand { Objects.requireNonNull(name,"name cannot be null");Objects.requireNonNull(slug,"slug cannot be null"); }
}