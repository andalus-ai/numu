package tech.kayys.numou.developer.apicatalog.application.command;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.List;
import java.util.Objects;
public record AddApiVersionCommand(String productId,String version,String openApiVersion,String artifactReference,String sha256,List<ScopeDefinition> scopes) implements Command {
 public AddApiVersionCommand { Objects.requireNonNull(productId,"productId cannot be null");Objects.requireNonNull(version,"version cannot be null");Objects.requireNonNull(openApiVersion,"openApiVersion cannot be null");Objects.requireNonNull(artifactReference,"artifactReference cannot be null");Objects.requireNonNull(sha256,"sha256 cannot be null");scopes=List.copyOf(Objects.requireNonNull(scopes,"scopes cannot be null")); }
 public record ScopeDefinition(String name,String description) { public ScopeDefinition { Objects.requireNonNull(name,"name cannot be null");Objects.requireNonNull(description,"description cannot be null"); } }
}