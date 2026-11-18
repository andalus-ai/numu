package tech.kayys.numou.content.application;
import tech.kayys.numou.content.domain.model.ContentValidationResult;
public final class ContentValidationException extends RuntimeException {
 private final ContentValidationResult result;
 public ContentValidationException(ContentValidationResult result){super("Content does not satisfy the selected published schema");this.result=result;}
 public ContentValidationResult result(){return result;}
}
