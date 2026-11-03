package com.flowdesk.auth.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    @Test
    @DisplayName("When trustProxyHeaders is disabled, strictly returns socket remoteAddr")
    void trustProxyHeadersDisabledAlwaysReturnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195");
        request.addHeader("X-Real-IP", "198.51.100.1");

        assertEquals("127.0.0.1", ClientIpResolver.resolveClientIp(request));
        assertEquals("127.0.0.1", ClientIpResolver.resolveClientIp(request, false));
    }

    @Test
    @DisplayName("Direct untrusted public connection cannot spoof IP via forwarded headers")
    void directUntrustedConnectionIgnoresForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.50"); // Public internet address (untrusted)
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        request.addHeader("X-Real-IP", "10.0.0.1");
        request.addHeader("CF-Connecting-IP", "8.8.8.8");

        // Even with trustProxyHeaders=true, because 198.51.100.50 is not an approved proxy, headers are ignored
        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("198.51.100.50", resolved, "Direct untrusted requests must never gain privileged identity");
    }

    @Test
    @DisplayName("Default trusted proxy (loopback) correctly extracts external client IP from X-Forwarded-For")
    void defaultTrustedProxyExtractsClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Default trusted local reverse proxy (Caddy on host)
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("203.0.113.195", resolved, "Trusted proxy must resolve true external client IP");
    }

    @Test
    @DisplayName("Malicious container on internal Docker network cannot forge arbitrary client identities by default")
    void unconfiguredInternalContainerCannotForgeClientIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5"); // Another container on the internal Docker network
        request.addHeader("X-Forwarded-For", "8.8.8.8"); // Forged client IP

        // By default, 172.18.0.5 is NOT trusted; only explicitly configured proxies or loopback are trusted
        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("172.18.0.5", resolved, "Internal containers cannot forge external client identities by default");
    }

    @Test
    @DisplayName("Explicitly configured proxy container IP or CIDR is trusted")
    void explicitlyConfiguredProxyIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.2"); // Ingress proxy container
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        // When 172.18.0.2 is declared as a trusted proxy:
        String resolved = ClientIpResolver.resolveClientIp(request, true, Set.of("172.18.0.2"));
        assertEquals("203.0.113.195", resolved);

        // When CIDR 172.18.0.0/16 is declared:
        String resolvedCidr = ClientIpResolver.resolveClientIp(request, true, Set.of("172.18.0.0/16"));
        assertEquals("203.0.113.195", resolvedCidr);
    }

    @Test
    @DisplayName("Corporate VPN client with private IP behind trusted proxy is recognized as authentic client")
    void corporateVpnClientBehindTrustedProxyIsNotTreatedAsProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Caddy reverse proxy
        request.addHeader("X-Forwarded-For", "10.0.1.50"); // Corporate employee VPN IP

        // 10.0.1.50 is NOT an internal proxy; it is the genuine client IP
        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("10.0.1.50", resolved, "Private IP clients behind trusted proxy must be preserved, not skipped");
    }

    @Test
    @DisplayName("Spoofed forwarded headers sent to reverse proxy are safely ignored (right-to-left parsing)")
    void spoofedForwardedHeadersDiscarded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Trusted local proxy (e.g. Caddy / Nginx)
        // Attacker prepended "1.1.1.1", proxy appended real client "203.0.113.195"
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.195");

        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("203.0.113.195", resolved, "Must parse right-to-left and stop at first untrusted IP");
    }

    @Test
    @DisplayName("Proxy chaining correctly traverses intermediate trusted proxies")
    void proxyChainingTraversesIntermediateProxies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Ingress proxy
        // Chain: Client (203.0.113.10) -> Intermediate Proxy (10.0.0.5) -> Ingress (127.0.0.1)
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.5");

        // When 10.0.0.5 is an explicitly configured intermediate proxy:
        String resolved = ClientIpResolver.resolveClientIp(request, true, Set.of("10.0.0.5"));
        assertEquals("203.0.113.10", resolved, "Must skip intermediate trusted proxies");
    }

    @Test
    @DisplayName("IPv6 addresses are validated and extracted without DNS resolution")
    void ipv6AddressExtractedCleanly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        request.addHeader("X-Forwarded-For", "2001:db8:85a3::8a2e:370:7334");

        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("2001:db8:85a3::8a2e:370:7334", resolved);
    }

    @Test
    @DisplayName("Malformed or injection forwarded headers fall back safely to socket address")
    void malformedForwardedHeadersFallbackSafely() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "malformed_ip, <script>, ; rm -rf /");

        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("127.0.0.1", resolved, "Malformed headers must safely fall back to remoteAddr");
    }

    @Test
    @DisplayName("X-Real-IP is used as fallback when X-Forwarded-For is absent")
    void xRealIpFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.88");

        String resolved = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("203.0.113.88", resolved);
    }

    @Test
    @DisplayName("Custom trusted proxy set is respected")
    void customTrustedProxiesRespected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.99"); // Normally untrusted public IP
        request.addHeader("X-Forwarded-For", "203.0.113.44");

        // When 198.51.100.99 is explicitly added to trusted proxies:
        String resolved = ClientIpResolver.resolveClientIp(request, true, Set.of("198.51.100.99"));
        assertEquals("203.0.113.44", resolved);
    }

    @Test
    @DisplayName("Handles null or empty remoteAddr safely")
    void handleNullOrEmptyRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        assertEquals("unknown", ClientIpResolver.resolveClientIp(request));

        request.setRemoteAddr("   ");
        assertEquals("unknown", ClientIpResolver.resolveClientIp(request));

        assertEquals("unknown", ClientIpResolver.resolveClientIp(null));
    }

    @Test
    @DisplayName("isTrustedProxy correctly classifies loopback vs unconfigured private/public IPs")
    void isTrustedProxyClassification() {
        // Loopback is always trusted
        assertTrue(ClientIpResolver.isTrustedProxy("127.0.0.1"));
        assertTrue(ClientIpResolver.isTrustedProxy("::1"));
        assertTrue(ClientIpResolver.isTrustedProxy("127.0.0.2"));

        // Private IPs are NOT trusted by default (defense against internal container spoofing)
        assertFalse(ClientIpResolver.isTrustedProxy("172.18.0.2"));
        assertFalse(ClientIpResolver.isTrustedProxy("10.0.5.1"));
        assertFalse(ClientIpResolver.isTrustedProxy("192.168.1.1"));

        // Explicitly configured private IPs or CIDRs ARE trusted
        assertTrue(ClientIpResolver.isTrustedProxy("172.18.0.2", Set.of("172.18.0.2")));
        assertTrue(ClientIpResolver.isTrustedProxy("172.18.0.2", Set.of("172.18.0.0/16")));
        assertTrue(ClientIpResolver.isTrustedProxy("10.0.5.1", Set.of("10.0.0.0/8")));

        // Public IPs and invalid inputs
        assertFalse(ClientIpResolver.isTrustedProxy("203.0.113.195"));
        assertFalse(ClientIpResolver.isTrustedProxy("8.8.8.8"));
        assertFalse(ClientIpResolver.isTrustedProxy("unknown"));
        assertFalse(ClientIpResolver.isTrustedProxy(null));
    }
}
