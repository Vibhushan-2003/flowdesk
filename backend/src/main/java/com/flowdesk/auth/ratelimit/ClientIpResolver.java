package com.flowdesk.auth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves client IP addresses with secure trusted-proxy validation.
 *
 * Security Design & Defense-in-Depth:
 * 1. Default (trustProxyHeaders = false): strictly defaults to request.getRemoteAddr() directly
 *    from the network socket. Safe default for direct connections and local development.
 * 2. When trustProxyHeaders = true:
 *    - Validates that request.getRemoteAddr() is an approved trusted internal proxy (loopback,
 *      Docker internal bridge networks, or private subnets).
 *    - If request.getRemoteAddr() is NOT a trusted proxy (e.g. direct internet connection),
 *      any forwarded headers are strictly ignored and the socket address is returned.
 *    - If request.getRemoteAddr() IS a trusted proxy: traverses X-Forwarded-For tokens from
 *      right to left (parsing hops backward). The first valid, untrusted IP encountered is returned
 *      as the authentic client IP. Intermediate trusted proxies are skipped.
 *    - Malformed or invalid IP tokens are rejected and skipped without exception.
 *    - Falls back to X-Real-IP if X-Forwarded-For is absent or empty.
 *    - Protects against header spoofing, IP injection, and rate-limit denial of service.
 */
public final class ClientIpResolver {

    public static final String UNKNOWN_IP = "unknown";
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String HEADER_X_REAL_IP = "X-Real-IP";

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}$"
    );

    private static final Pattern IPV6_HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{1,4}$");

    private ClientIpResolver() {
    }

    /**
     * Resolves the client IP strictly from request.getRemoteAddr().
     * Forwarded headers are ignored to guarantee socket-level identity.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        return resolveClientIp(request, false);
    }

    /**
     * Resolves the client IP with optional trusted-proxy header inspection.
     *
     * @param request           the HTTP request
     * @param trustProxyHeaders whether to inspect forwarded headers when the immediate remoteAddr is a trusted proxy
     * @return the resolved client IP, or "unknown" if unresolvable
     */
    public static String resolveClientIp(HttpServletRequest request, boolean trustProxyHeaders) {
        return resolveClientIp(request, trustProxyHeaders, Collections.emptySet());
    }

    /**
     * Resolves the client IP with optional trusted-proxy header inspection and custom trusted proxies.
     *
     * @param request                 the HTTP request
     * @param trustProxyHeaders       whether to inspect forwarded headers when remoteAddr is trusted
     * @param additionalTrustedProxies additional trusted proxy IPs (optional)
     * @return the resolved client IP, or "unknown" if unresolvable
     */
    public static String resolveClientIp(
            HttpServletRequest request,
            boolean trustProxyHeaders,
            Set<String> additionalTrustedProxies
    ) {
        if (request == null) {
            return UNKNOWN_IP;
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return UNKNOWN_IP;
        }

        remoteAddr = stripPortAndBrackets(remoteAddr.trim());
        if (!trustProxyHeaders) {
            return remoteAddr;
        }

        // Defense boundary: Only trust forwarded headers if the immediate peer is a verified trusted proxy
        if (!isTrustedProxy(remoteAddr, additionalTrustedProxies)) {
            return remoteAddr;
        }

        // Peer is trusted. Inspect X-Forwarded-For from right to left (most recent hop first)
        String xForwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] tokens = xForwardedFor.split(",");
            for (int i = tokens.length - 1; i >= 0; i--) {
                String candidate = stripPortAndBrackets(tokens[i].trim());
                if (!isValidIp(candidate)) {
                    // Skip malformed/invalid IP tokens
                    continue;
                }
                if (isTrustedProxy(candidate, additionalTrustedProxies)) {
                    // Skip intermediate trusted proxies in the chain
                    continue;
                }
                // First valid untrusted IP from the right is the true client IP
                return candidate;
            }
        }

        // Fallback: inspect X-Real-IP if X-Forwarded-For did not yield an untrusted client IP
        String xRealIp = request.getHeader(HEADER_X_REAL_IP);
        if (xRealIp != null && !xRealIp.isBlank()) {
            String candidate = stripPortAndBrackets(xRealIp.trim());
            if (isValidIp(candidate) && !isTrustedProxy(candidate, additionalTrustedProxies)) {
                return candidate;
            }
        }

        // If all forwarded IPs were internal proxies or absent, fall back to remoteAddr
        return remoteAddr;
    }

    /**
     * Validates whether a candidate string is a well-formed IPv4 or IPv6 address.
     * Uses allocation-free regex/character parsing without DNS resolution.
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String clean = stripPortAndBrackets(ip.trim());
        return isValidIpv4(clean) || isValidIpv6(clean);
    }

    /**
     * Validates an IPv4 address.
     */
    public static boolean isValidIpv4(String ip) {
        if (ip == null || ip.length() < 7 || ip.length() > 15) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * Validates an IPv6 address without DNS lookup.
     */
    public static boolean isValidIpv6(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String clean = stripPortAndBrackets(ip.trim());
        if (!clean.contains(":")) {
            return false;
        }
        int doubleColonCount = 0;
        int idx = 0;
        while ((idx = clean.indexOf("::", idx)) != -1) {
            doubleColonCount++;
            idx += 2;
        }
        if (doubleColonCount > 1) {
            return false;
        }

        String[] parts = clean.split(":", -1);
        if (parts.length < 3 || parts.length > 8) {
            return false;
        }

        for (String part : parts) {
            if (part.isEmpty()) {
                continue; // Allowed around ::
            }
            if (part.length() > 4 || !IPV6_HEX_PATTERN.matcher(part).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if an IP address belongs to the trusted internal proxy set.
     * By default, ONLY loopback addresses are considered trusted proxies:
     * - Loopback (127.0.0.1, ::1, 0:0:0:0:0:0:0:1, localhost, 127.0.0.0/8)
     *
     * Private subnets (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) are strictly NOT trusted by default
     * to prevent malicious containers on internal Docker networks or direct LAN connections from forging
     * client identities. Additional proxies (e.g. specific reverse proxy container IPs or CIDRs)
     * must be explicitly declared via additionalTrustedProxies.
     */
    public static boolean isTrustedProxy(String ip) {
        return isTrustedProxy(ip, Collections.emptySet());
    }

    public static boolean isTrustedProxy(String ip, Set<String> additionalTrustedProxies) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String clean = stripPortAndBrackets(ip.trim());

        if (additionalTrustedProxies != null && !additionalTrustedProxies.isEmpty()) {
            for (String trusted : additionalTrustedProxies) {
                if (trusted != null && !trusted.isBlank()) {
                    String cleanTrusted = stripPortAndBrackets(trusted.trim());
                    if (matchesProxy(clean, cleanTrusted)) {
                        return true;
                    }
                }
            }
        }

        if ("127.0.0.1".equals(clean) || "::1".equals(clean) || "0:0:0:0:0:0:0:1".equals(clean) || "localhost".equalsIgnoreCase(clean)) {
            return true;
        }

        if (isValidIpv4(clean)) {
            String[] octets = clean.split("\\.");
            int first = Integer.parseInt(octets[0]);
            if (first == 127) {
                return true; // Loopback 127.0.0.0/8
            }
        }

        return false;
    }

    private static boolean matchesProxy(String ip, String rule) {
        if (ip.equalsIgnoreCase(rule)) {
            return true;
        }
        if (rule.contains("/")) {
            return matchesCidr(ip, rule);
        }
        return false;
    }

    private static boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            String network = parts[0].trim();
            int prefixLen = Integer.parseInt(parts[1].trim());

            if (isValidIpv4(ip) && isValidIpv4(network)) {
                if (prefixLen < 0 || prefixLen > 32) {
                    return false;
                }
                long ipLong = ipv4ToLong(ip);
                long netLong = ipv4ToLong(network);
                long mask = prefixLen == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixLen)) & 0xFFFFFFFFL;
                return (ipLong & mask) == (netLong & mask);
            }
        } catch (Exception ignored) {
            // Malformed CIDR safely returns false
        }
        return false;
    }

    private static long ipv4ToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (Integer.parseInt(octets[i]) & 0xFF);
        }
        return result;
    }

    /**
     * Strips square brackets from IPv6 and optional port suffix (e.g. [2001:db8::1]:443 or 1.2.3.4:8080).
     */
    public static String stripPortAndBrackets(String ip) {
        if (ip == null) {
            return "";
        }
        String s = ip.trim();
        if (s.startsWith("[") && s.contains("]")) {
            int close = s.indexOf(']');
            return s.substring(1, close);
        }
        // IPv4 with port (e.g. 192.168.1.1:8080)
        if (s.contains(".") && s.contains(":") && s.indexOf(':') == s.lastIndexOf(':')) {
            return s.substring(0, s.indexOf(':'));
        }
        return s;
    }
}
