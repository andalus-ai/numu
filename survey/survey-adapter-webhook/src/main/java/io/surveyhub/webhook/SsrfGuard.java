package io.surveyhub.webhook;

import io.surveyhub.application.port.out.WebhookUrlPolicy;
import io.surveyhub.domain.DomainException;
import io.surveyhub.domain.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Server-side request forgery protection for webhook targets: https only, no credentials in the URL, and every address the
 * host resolves to must be publicly routable (no loopback, private, link-local / cloud-metadata, carrier-grade NAT, multicast,
 * reserved or tunnel ranges). The check runs at registration and again right before each delivery.
 *
 * <p>Residual risk: DNS can change between the check and the connection (rebinding). For strict environments also run
 * deliveries through an egress proxy that enforces the same rules.
 */
public final class SsrfGuard implements WebhookUrlPolicy {
    private static final int MAX_URL = 2048;
    private final boolean allowPrivate;
    private final boolean allowHttp;

    /** @param allowPrivate permit internal addresses (development / tests only)
     *  @param allowHttp    permit plain http (development / tests only) */
    public SsrfGuard(boolean allowPrivate, boolean allowHttp) {
        this.allowPrivate = allowPrivate;
        this.allowHttp = allowHttp;
    }

    /** Production defaults: https and public addresses only. */
    public SsrfGuard() { this(false, false); }

    @Override
    public void check(String url) {
        if (url == null || url.length() > MAX_URL) throw bad("URL is missing or too long");
        URI u;
        try { u = new URI(url); } catch (java.net.URISyntaxException e) { throw bad("URL is not valid"); }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if (!(scheme.equals("https") || (allowHttp && scheme.equals("http")))) throw bad("Only https URLs are allowed");
        if (u.getRawUserInfo() != null) throw bad("Credentials in the URL are not allowed");
        String host = u.getHost();
        if (host == null || host.isBlank()) throw bad("URL has no host");
        InetAddress[] addresses;
        try { addresses = InetAddress.getAllByName(host); } catch (UnknownHostException e) { throw bad("Host does not resolve"); }
        if (!allowPrivate) {
            for (InetAddress a : addresses)
                if (isInternal(a)) throw bad("Target address is not publicly routable");
        }
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
        if (b.length == 4) {
            int b2 = b[2] & 0xFF;
            return b0 == 0                                   // "this" network
                    || (b0 == 100 && (b1 & 0xC0) == 64)      // 100.64.0.0/10 carrier-grade NAT
                    || (b0 == 192 && b1 == 0 && b2 == 0)     // 192.0.0.0/24 protocol assignments
                    || (b0 == 198 && (b1 & 0xFE) == 18)      // 198.18.0.0/15 benchmarking
                    || b0 >= 240;                            // reserved + broadcast
        }
        return (b0 & 0xFE) == 0xFC                           // fc00::/7 unique local
                || (b0 == 0x20 && b1 == 0x02)                // 2002::/16 6to4
                || (b0 == 0x20 && b1 == 0x01 && b[2] == 0 && b[3] == 0) // 2001::/32 Teredo
                || (b0 == 0x00 && b1 == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B); // 64:ff9b::/96 NAT64
    }

    private static DomainException bad(String m) { return new DomainException(ErrorCode.VALIDATION_FAILED, m); }
}
