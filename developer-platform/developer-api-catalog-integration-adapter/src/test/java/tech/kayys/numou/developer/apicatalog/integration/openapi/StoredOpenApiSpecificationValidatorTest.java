package tech.kayys.numou.developer.apicatalog.integration.openapi;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.developer.apicatalog.domain.ApiSpecification;
import tech.kayys.numou.documents.storage.spi.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

class StoredOpenApiSpecificationValidatorTest {
 private static final String VALID="""
  openapi: 3.1.0
  info:
    title: Orders API
    version: 1.0.0
  paths: {}
  """;

 @Test void verifiesTenantScopedArtifactDigestAndOpenApiVersion(){
  TenantId tenant=TenantId.generate();byte[] bytes=VALID.getBytes(StandardCharsets.UTF_8);String digest=sha(bytes);
  var validator=new StoredOpenApiSpecificationValidator(new MemoryStorage(bytes,digest));
  validator.validateAndVerify(tenant,new ApiSpecification("3.1","rustfs://api-specs/orders.yaml",digest)).await().indefinitely();
 }

 @Test void rejectsDigestMismatchBeforeOpenApiParsing(){
  TenantId tenant=TenantId.generate();byte[] bytes=VALID.getBytes(StandardCharsets.UTF_8);
  var validator=new StoredOpenApiSpecificationValidator(new MemoryStorage(bytes,sha(bytes)));
  assertThatThrownBy(()->validator.validateAndVerify(tenant,
    new ApiSpecification("3.1","rustfs://api-specs/orders.yaml","b".repeat(64))).await().indefinitely())
    .hasRootCauseInstanceOf(StoredOpenApiSpecificationValidator.ApiSpecificationValidationException.class);
 }

 @Test void rejectsMalformedOpenApiDocument(){
  TenantId tenant=TenantId.generate();byte[] bytes="not: [valid".getBytes(StandardCharsets.UTF_8);String digest=sha(bytes);
  var validator=new StoredOpenApiSpecificationValidator(new MemoryStorage(bytes,digest));
  assertThatThrownBy(()->validator.validateAndVerify(tenant,
    new ApiSpecification("3.1","rustfs://api-specs/orders.yaml",digest)).await().indefinitely())
    .hasRootCauseInstanceOf(StoredOpenApiSpecificationValidator.ApiSpecificationValidationException.class);
 }

 private static String sha(byte[] bytes){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 private static final class MemoryStorage implements DocumentStorage{
  private final byte[] bytes;private final String digest;
  MemoryStorage(byte[] bytes,String digest){this.bytes=bytes.clone();this.digest=digest;}
  public StoredObjectMetadata put(StorageKey key,InputStream in,long length,String type,String sha){throw new UnsupportedOperationException();}
  public Optional<StoredObject> get(StorageKey key){
   if(!key.objectId().equals("api-specs/orders.yaml"))return Optional.empty();
   var metadata=new StoredObjectMetadata(key,bytes.length,"application/yaml",digest,Instant.parse("2026-11-04T10:00:00Z"),null);
   return Optional.of(new StoredObject(){
    public StoredObjectMetadata metadata(){return metadata;}
    public InputStream content(){return new ByteArrayInputStream(bytes);}
    public void close(){}
   });
  }
  public Optional<StoredObjectMetadata> head(StorageKey key){return Optional.empty();}
  public boolean delete(StorageKey key){return false;}
 }
}
