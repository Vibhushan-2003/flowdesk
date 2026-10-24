package com.flowdesk.auth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP address.
 *
 * Security Design:
 * - Strictly defaults to request.getRemoteAddr() directly from the network socket.
 * - Header-based forwarding (X-Forwarded-For, Forwarded, CF-Connecting-IP) is completely
 *   untrusted at the application tier because arbitrary clients can forge them.
 * - Production reverse-proxy topologies should handle IP stripping/replacement at the edge
 *   or container boundary (e.g., Nginx RemoteIPValve or Cloudflare authenticated origin pull).
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "unknown";
    }

    /**
     * Overloaded resolution for backward compatibility.
     * Note: untrusted forwarding headers are intentionally ignored regardless of the flag to
     * prevent spoofing. Reverse proxy topologies must configure RemoteIpValve or edge stripping.
     */
    public static String resolveClientIp(HttpServletRequest request, boolean trustProxyHeaders) {
        return resolveClientIp(request);
    }
}

