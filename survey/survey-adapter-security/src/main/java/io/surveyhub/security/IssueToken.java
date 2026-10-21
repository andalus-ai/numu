package io.surveyhub.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Developer tool: prints a signed token.
 * <pre>java -cp ... io.surveyhub.security.IssueToken &lt;secret&gt; &lt;tenant&gt; &lt;subject&gt; &lt;roles,comma,separated&gt; [ttlMinutes]</pre>
 */
public final class IssueToken {
    private IssueToken() {}

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("usage: IssueToken <secret(>=32 chars)> <tenant> <subject> <ROLE[,ROLE]> [ttlMinutes=60]");
            System.exit(2);
        }
        long ttl = args.length > 4 ? Long.parseLong(args[4]) : 60;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", args[2]);
        claims.put("tenant", args[1]);
        claims.put("roles", Arrays.asList(args[3].split(",")));
        claims.put("exp", Instant.now().plusSeconds(ttl * 60).getEpochSecond());
        System.out.println(JwtVerifier.sign(args[0].getBytes(StandardCharsets.UTF_8), claims));
    }
}
