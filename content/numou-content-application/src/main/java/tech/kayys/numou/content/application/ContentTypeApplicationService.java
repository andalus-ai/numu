package tech.kayys.numou.content.application;
import tech.kayys.numou.content.domain.model.*;
import tech.kayys.numou.content.spi.ContentTypeRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** Use cases for immutable, tenant-scoped content-type schema versions. */
public final class ContentTypeApplicationService {
 private final ContentTypeRepository repository;
 private final ContentValueValidator validator;
 private final Clock clock;
 public ContentTypeApplicationService(ContentTypeRepository repository, Clock clock) {
  this(repository, new ContentValueValidator(), clock);
 }
 public ContentTypeApplicationService(ContentTypeRepository repository, ContentValueValidator validator, Clock clock) {
  this.repository=Objects.requireNonNull(repository); this.validator=Objects.requireNonNull(validator); this.clock=Objects.requireNonNull(clock);
 }
 public ContentTypeSchema createType(ContentCommandContext context, String key, String displayName, List<ContentFieldDefinition> fields) {
  ContentTypeSchema schema=ContentTypeSchema.create(context.tenantId(),ContentTypeId.newId(),key,displayName,fields,clock.instant());
  repository.createType(schema); return schema;
 }
 public ContentTypeSchema createVersion(ContentCommandContext context, ContentTypeId typeId, int baseVersion, List<ContentFieldDefinition> fields) {
  ContentTypeSchema next=get(context,typeId,baseVersion).nextDraft(fields,clock.instant());
  repository.createVersion(next); return next;
 }
 public ContentTypeSchema publish(ContentCommandContext context, ContentTypeId typeId, int version) {
  ContentTypeSchema published=get(context,typeId,version).publish(clock.instant());
  if(!repository.publish(context.tenantId(),typeId,version,published.publishedAt()))
   throw new IllegalStateException("Schema version changed concurrently or is already published");
  return published;
 }
 public ContentTypeSchema get(ContentCommandContext context, ContentTypeId typeId, int version) {
  return repository.findVersion(context.tenantId(),typeId,version).orElseThrow(ContentNotFoundException::new);
 }
 /** Persisted content must validate against an explicitly selected published schema version. */
 public ContentValidationResult validate(ContentCommandContext context, ContentTypeId typeId, int version, Map<String,?> values) {
  ContentTypeSchema schema=get(context,typeId,version);
  if(schema.status()!=SchemaStatus.PUBLISHED) throw new IllegalStateException("Only published schema versions may validate persisted content");
  return validator.validate(schema,values);
 }
 /** Draft preview validation is deliberately separate from published-content validation. */
 public ContentValidationResult validateDraft(ContentCommandContext context, ContentTypeId typeId, int version, Map<String,?> values) {
  return validator.validate(get(context,typeId,version),values);
 }
}
