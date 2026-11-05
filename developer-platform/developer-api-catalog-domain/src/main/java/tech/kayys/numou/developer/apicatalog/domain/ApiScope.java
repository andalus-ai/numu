package tech.kayys.numou.developer.apicatalog.domain;
import java.util.Objects;
/** Published authorization vocabulary; defining a scope does not grant it. */
public record ApiScope(String name, String description) {
 public ApiScope {
  name = text(name, "name"); description = text(description, "description");
  if (!name.matches("[a-z][a-z0-9]*(?:[.:][a-z0-9][a-z0-9-]*)*")) throw new IllegalArgumentException("invalid scope name: " + name);
  if (description.length() > 500) throw new IllegalArgumentException("description exceeds 500 characters");
 }
 private static String text(String value,String field) { Objects.requireNonNull(value,field+" cannot be null"); String v=value.trim(); if(v.isEmpty()) throw new IllegalArgumentException(field+" cannot be blank"); return v; }
}