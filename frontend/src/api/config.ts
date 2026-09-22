const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL

if (!apiBaseUrl) {
  throw new Error(
    'VITE_API_BASE_URL is not configured',
  )
}

/*
 * Remove trailing slashes so REST URLs do not
 * accidentally become:
 *
 * http://localhost:8080//api/...
 */
export const API_BASE_URL =
  apiBaseUrl.replace(/\/+$/, '')

/*
 * Convert the normal API origin into the
 * WebSocket endpoint.
 *
 * Development:
 * http://localhost:8080
 *        ↓
 * ws://localhost:8080/ws
 *
 * Production:
 * https://api.example.com
 *        ↓
 * wss://api.example.com/ws
 */
function createWebSocketUrl(
  baseUrl: string,
) {
  const url =
    new URL(baseUrl)

  if (url.protocol === 'http:') {
    url.protocol = 'ws:'
  } else if (
    url.protocol === 'https:'
  ) {
    url.protocol = 'wss:'
  } else {
    throw new Error(
      'VITE_API_BASE_URL must use http or https',
    )
  }

  url.pathname =
    `${url.pathname.replace(
      /\/+$/,
      '',
    )}/ws`

  url.search = ''
  url.hash = ''

  return url.toString()
}

export const WEBSOCKET_URL =
  createWebSocketUrl(
    API_BASE_URL,
  )