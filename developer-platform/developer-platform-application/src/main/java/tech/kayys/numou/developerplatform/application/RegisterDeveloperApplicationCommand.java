package tech.kayys.numou.developerplatform.application;
public record RegisterDeveloperApplicationCommand(String slug,String name,String description,String idempotencyKey){
 public RegisterDeveloperApplicationCommand{if(idempotencyKey==null||idempotencyKey.trim().length()<16||idempotencyKey.trim().length()>200)throw new IllegalArgumentException("idempotencyKey must contain 16-200 characters");idempotencyKey=idempotencyKey.trim();}
}