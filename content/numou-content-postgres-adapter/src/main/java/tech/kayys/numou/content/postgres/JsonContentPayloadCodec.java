package tech.kayys.numou.content.postgres;

import tech.kayys.numou.content.spi.ContentPayloadCodec;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Small dependency-free JSON encoder for already-validated content maps. */
public final class JsonContentPayloadCodec implements ContentPayloadCodec {
    @Override public String encode(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        StringBuilder out=new StringBuilder();
        write(values,out,new IdentityHashMap<>(),0);
        return out.toString();
    }

    private void write(Object value,StringBuilder out,IdentityHashMap<Object,Boolean> visiting,int depth) {
        if(depth>64) throw new IllegalArgumentException("Content JSON nesting exceeds 64 levels");
        if(value==null){out.append("null");return;}
        if(value instanceof String s){quote(s,out);return;}
        if(value instanceof Boolean b){out.append(b);return;}
        if(value instanceof Number n){number(n,out);return;}
        if(value instanceof Map<?,?> map){
            enter(value,visiting);out.append('{');
            List<String> keys=new ArrayList<>();
            for(Object key:map.keySet()){
                if(!(key instanceof String)) throw new IllegalArgumentException("JSON object keys must be strings");
                keys.add((String)key);
            }
            Collections.sort(keys);
            boolean first=true;
            for(String key:keys){if(!first)out.append(',');first=false;quote(key,out);out.append(':');write(map.get(key),out,visiting,depth+1);}
            out.append('}');visiting.remove(value);return;
        }
        if(value instanceof Iterable<?> iterable){
            enter(value,visiting);out.append('[');boolean first=true;
            for(Object item:iterable){if(!first)out.append(',');first=false;write(item,out,visiting,depth+1);}
            out.append(']');visiting.remove(value);return;
        }
        if(value.getClass().isArray()){
            enter(value,visiting);out.append('[');
            for(int i=0;i<Array.getLength(value);i++){if(i>0)out.append(',');write(Array.get(value,i),out,visiting,depth+1);}
            out.append(']');visiting.remove(value);return;
        }
        throw new IllegalArgumentException("Unsupported JSON value type: "+value.getClass().getName());
    }

    private void enter(Object value,IdentityHashMap<Object,Boolean> visiting) {
        if(visiting.put(value,Boolean.TRUE)!=null) throw new IllegalArgumentException("Cyclic content values cannot be encoded as JSON");
    }
    private void number(Number value,StringBuilder out) {
        if(value instanceof Double d && !Double.isFinite(d)) throw new IllegalArgumentException("JSON numbers must be finite");
        if(value instanceof Float f && !Float.isFinite(f)) throw new IllegalArgumentException("JSON numbers must be finite");
        try {
            BigDecimal decimal=value instanceof BigDecimal bd?bd:new BigDecimal(value.toString());
            out.append(decimal.stripTrailingZeros().toPlainString());
        } catch(NumberFormatException e) {
            if(value instanceof BigInteger bi) out.append(bi);
            else throw new IllegalArgumentException("Invalid JSON number",e);
        }
    }
    private void quote(String value,StringBuilder out) {
        out.append('"');
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> {if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
            }
        }
        out.append('"');
    }
}
