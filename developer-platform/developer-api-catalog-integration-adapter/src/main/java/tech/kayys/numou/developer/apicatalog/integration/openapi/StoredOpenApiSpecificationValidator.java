package tech.kayys.numou.developer.apicatalog.integration.openapi;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import tech.kayys.numou.developer.apicatalog.application.port.ApiSpecificationValidationPort;
import tech.kayys.numou.developer.apicatalog.domain.ApiSpecification;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies tenant-scoped artifact existence, size, content digest and OpenAPI 3.0/3.1 semantics.
 * OpenAPI parsing is local-only: external $ref resolution is disabled to prevent network fetches.
 */
public final class StoredOpenApiSpecificationValidator implements ApiSpecificationValidationPort {
 private static final Set<String> SUPPORTED_SCHEMES=Set.of("s3","rustfs");
 private final DocumentStorage storage;
 private final int maxBytes;

 public StoredOpenApiSpecificationValidator(DocumentStorage storage){
  this(storage,10*1024*1024);
 }
 public StoredOpenApiSpecificationValidator(DocumentStorage storage,int maxBytes){
  this.storage=Objects.requireNonNull(storage,"storage cannot be null");
  if(maxBytes<1024)throw new IllegalArgumentException("maxBytes must be at least 1024");
  this.maxBytes=maxBytes;
 }

 @Override public Uni<Void> validateAndVerify(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                                               ApiSpecification specification){
  Objects.requireNonNull(tenant,"tenant cannot be null");
  Objects.requireNonNull(specification,"specification cannot be null");
  return Uni.createFrom().item(()->{
   validateBlocking(tenant.value().toString(),specification);
   return (Void)null;
  }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
 }

 private void validateBlocking(String tenantId,ApiSpecification specification){
  StorageKey key=storageKey(tenantId,specification.artifactReference());
  try{
   var stored=storage.get(key).orElseThrow(()->new ApiSpecificationValidationException("OpenAPI artifact does not exist for provider tenant"));
   try(stored){
    if(!stored.metadata().key().tenantId().equals(tenantId)
       ||!stored.metadata().key().objectId().equals(key.objectId())){
     throw new ApiSpecificationValidationException("Document storage returned an artifact outside the requested tenant key");
    }
    long declaredLength=stored.metadata().contentLength();
    if(declaredLength<1||declaredLength>maxBytes)throw new ApiSpecificationValidationException("OpenAPI artifact size is outside the permitted range");
    byte[] bytes=readBounded(stored.content(),maxBytes);
    if(bytes.length!=declaredLength)throw new ApiSpecificationValidationException("OpenAPI artifact length does not match stored metadata");
    String actualSha256=sha256(bytes);
    if(!constantTimeEquals(specification.sha256(),actualSha256))throw new ApiSpecificationValidationException("OpenAPI artifact SHA-256 digest mismatch");
    stored.metadata().sha256().ifPresent(metadataDigest->{
     if(!constantTimeEquals(specification.sha256(),metadataDigest.toLowerCase(Locale.ROOT)))
      throw new ApiSpecificationValidationException("Document storage checksum metadata does not match catalog digest");
    });
    parseAndValidate(bytes,specification);
   }
  }catch(IOException failure){
   throw new ApiSpecificationValidationException("Unable to read tenant-owned OpenAPI artifact",failure);
  }
 }

 private static void parseAndValidate(byte[] bytes,ApiSpecification specification){
  String document=new String(bytes,StandardCharsets.UTF_8);
  ParseOptions options=new ParseOptions();
  options.setResolve(false);
  options.setResolveFully(false);
  options.setFlatten(false);
  SwaggerParseResult parsed=new OpenAPIV3Parser().readContents(document,null,options);
  OpenAPI openApi=parsed==null?null:parsed.getOpenAPI();
  if(openApi==null)throw new ApiSpecificationValidationException("OpenAPI document is syntactically invalid or has no OpenAPI model");
  if(parsed.getMessages()!=null&&!parsed.getMessages().isEmpty())
   throw new ApiSpecificationValidationException("OpenAPI parser reported validation diagnostics: "+String.join("; ",parsed.getMessages()));
  String actualVersion=openApi.getOpenapi();
  if(actualVersion==null||!actualVersion.startsWith(specification.openApiVersion()+"."))
   throw new ApiSpecificationValidationException("OpenAPI document version does not match catalog metadata");
 }

 private static StorageKey storageKey(String tenantId,String reference){
  try{
   URI uri=URI.create(reference);
   String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
   if(!SUPPORTED_SCHEMES.contains(scheme)||uri.getHost()==null||uri.getHost().isBlank()
      ||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()!=-1)
    throw new ApiSpecificationValidationException("Unsupported or unsafe OpenAPI artifact reference");
   String path=uri.getRawPath();
   if(path==null||path.length()<2||path.contains("%2f")||path.contains("%2F"))
    throw new ApiSpecificationValidationException("OpenAPI artifact reference must identify a storage object path");
   String objectId=uri.getHost()+path;
   return new StorageKey(tenantId,objectId);
  }catch(ApiSpecificationValidationException validation){
   throw validation;
  }catch(IllegalArgumentException invalid){
   throw new ApiSpecificationValidationException("Invalid OpenAPI artifact reference",invalid);
  }
 }

 private static byte[] readBounded(InputStream input,int maxBytes)throws IOException{
  byte[] bytes=input.readNBytes(maxBytes+1);
  if(bytes.length>maxBytes)throw new ApiSpecificationValidationException("OpenAPI artifact exceeds configured size limit");
  return bytes;
 }
 private static String sha256(byte[] bytes){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 is unavailable",impossible);}
 }
 private static boolean constantTimeEquals(String expected,String actual){
  return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),actual.getBytes(StandardCharsets.US_ASCII));
 }

 public static final class ApiSpecificationValidationException extends RuntimeException{
  public ApiSpecificationValidationException(String message){super(message);}
  public ApiSpecificationValidationException(String message,Throwable cause){super(message,cause);}
 }
}
