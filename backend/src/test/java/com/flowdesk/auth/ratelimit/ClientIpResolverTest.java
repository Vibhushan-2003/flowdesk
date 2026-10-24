package com.flowdesk.auth.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    @DisplayName("Should use getRemoteAddr and strictly ignore untrusted X-Forwarded-For spoofing")
    void untrustedForwardedHeadersIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        request.addHeader("X-Real-IP", "10.0.0.1");
        request.addHeader("CF-Connecting-IP", "198.51.100.1");
        request.addHeader("Forwarded", "for=192.0.2.60;proto=http;by=203.0.113.43");

        String ip = ClientIpResolver.resolveClientIp(request);
        assertEquals("192.168.1.50", ip, "Spoofed forwarding headers must be strictly ignored");

        // Overloaded variant should also ignore headers regardless of boolean flag
        String ipWithFlag = ClientIpResolver.resolveClientIp(request, true);
        assertEquals("192.168.1.50", ipWithFlag, "Header spoofing must never influence client IP resolution");
    }

    @Test
    @DisplayName("Should return unknown when remoteAddr is null or empty")
    void handleNullOrEmptyRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        assertEquals("unknown", ClientIpResolver.resolveClientIp(request));

        request.setRemoteAddr("   ");
        assertEquals("unknown", ClientIpResolver.resolveClientIp(request));

        assertEquals("unknown", ClientIpResolver.resolveClientIp(null));
    }
}
