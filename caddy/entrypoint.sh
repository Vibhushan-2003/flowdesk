#!/bin/sh
set -e

# ==============================================================================
# FlowDesk Caddy Production Ingress Entrypoint - Fail-Fast Validation Gate
# ==============================================================================
# Ensures strict separation between:
#   1. Isolated Local Verification (CADDY_DOMAIN=localhost, CADDY_TLS=internal)
#   2. Public Production Deployment (CADDY_DOMAIN=<fqdn>, CADDY_TLS=<operator-email>)
# ==============================================================================

DOMAIN="${CADDY_DOMAIN:-localhost}"
TLS_POLICY="${CADDY_TLS:-internal}"
ORIGIN="${FRONTEND_ORIGIN:-https://localhost}"

echo "[Caddy-Init] Validating ingress configuration..."
echo "[Caddy-Init] CADDY_DOMAIN    = ${DOMAIN}"
echo "[Caddy-Init] CADDY_TLS       = ${TLS_POLICY}"
echo "[Caddy-Init] FRONTEND_ORIGIN = ${ORIGIN}"

# 1. Local Isolated Testing Mode (localhost / 127.0.0.1)
if [ "$DOMAIN" = "localhost" ] || [ "$DOMAIN" = "127.0.0.1" ]; then
    if [ "$TLS_POLICY" != "internal" ]; then
        echo "[FATAL] CADDY_DOMAIN is '$DOMAIN' (local testing) but CADDY_TLS is '$TLS_POLICY'." >&2
        echo "[FATAL] Public ACME certificate authorities (Let's Encrypt / ZeroSSL) cannot issue certificates for localhost." >&2
        echo "[FATAL] Fix: Set CADDY_TLS=internal in your environment file." >&2
        exit 1
    fi
    echo "[Caddy-Init] Operating in Isolated Local Verification Mode (Caddy Internal CA)."

# 2. Public Production Mode (Public DNS FQDN)
else
    if [ "$TLS_POLICY" = "internal" ]; then
        echo "[FATAL] CADDY_DOMAIN is set to public domain '$DOMAIN' but CADDY_TLS is set to 'internal' (self-signed CA)." >&2
        echo "[FATAL] Public browsers will reject untrusted self-signed certificates." >&2
        echo "[FATAL] Fix: Set CADDY_TLS=<operator-email> (e.g. admin@$DOMAIN) in your environment file to obtain automated ACME TLS certificates." >&2
        exit 1
    fi

    # Verify CADDY_TLS contains an '@' symbol
    case "$TLS_POLICY" in
        *@*.*) ;;
        *)
            echo "[FATAL] CADDY_TLS ('$TLS_POLICY') does not appear to be a valid operator email address for ACME registration." >&2
            echo "[FATAL] Fix: Provide a valid contact email in CADDY_TLS (e.g. ops@$DOMAIN)." >&2
            exit 1
            ;;
    esac

    # Verify FRONTEND_ORIGIN starts with https://$DOMAIN
    case "$ORIGIN" in
        https://"$DOMAIN"|https://"$DOMAIN":*)
            ;;
        *)
            echo "[FATAL] Mismatched origins detected:" >&2
            echo "[FATAL]   CADDY_DOMAIN    = $DOMAIN" >&2
            echo "[FATAL]   FRONTEND_ORIGIN = $ORIGIN" >&2
            echo "[FATAL] Fix: FRONTEND_ORIGIN must start with https://$DOMAIN (e.g. https://$DOMAIN)." >&2
            exit 1
            ;;
    esac

    echo "[Caddy-Init] Operating in Public Production Mode with automated ACME TLS for $DOMAIN (contact: $TLS_POLICY)."
fi

echo "[Caddy-Init] Validation passed. Launching Caddy reverse proxy..."
exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
