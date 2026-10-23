package tech.kayys.numou.workspace.persistence.jdbc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Deterministic, delimiter-safe encoding for block metadata without coupling the adapter to a JSON library. */
final class BlockAttributesCodec {
 private BlockAttributesCodec() {}
 static String encode(Map<String,String> attributes) {
  StringJoiner out=new StringJoiner(";");Base64.Encoder e=Base64.getUrlEncoder().withoutPadding();
  attributes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(x->out.add(e.encodeToString(x.getKey().getBytes(StandardCharsets.UTF_8))+":"+e.encodeToString(x.getValue().getBytes(StandardCharsets.UTF_8))));
  return out.toString();
 }
 static Map<String,String> decode(String value) {
  if(value==null||value.isEmpty())return Map.of();Map<String,String> out=new LinkedHashMap<>();Base64.Decoder d=Base64.getUrlDecoder();
  for(String item:value.split(";")){int i=item.indexOf(':');if(i<0)throw new IllegalStateException("Malformed encoded block attributes");String k=new String(d.decode(item.substring(0,i)),StandardCharsets.UTF_8);String v=new String(d.decode(item.substring(i+1)),StandardCharsets.UTF_8);out.put(k,v);}
  return Map.copyOf(out);
 }
}
