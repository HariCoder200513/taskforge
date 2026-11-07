// Thin API client for the TaskForge backend.
//
// Auth model (mirrors the backend design):
//   - The ACCESS token (short-lived JWT) is kept ONLY in memory — never in
//     localStorage — so an XSS can't read a persisted token. It's lost on reload
//     and re-obtained via the refresh cookie (see refreshSession()).
//   - The REFRESH token lives in an HttpOnly, SameSite=Strict cookie the browser
//     sends automatically (credentials:'include'); JS never sees it.
//   - Cookie endpoints (/auth/refresh, /auth/logout) require a custom header
//     (X-CSRF-Token) — its mere presence defeats simple cross-site form posts —
//     plus an allowlisted Origin. We send it on every POST; it's ignored elsewhere.

const BASE = '/api/v1'

let accessToken = null
let onUnauthenticated = () => {}

export function setAccessToken(token) { accessToken = token }
export function getAccessToken() { return accessToken }
export function setOnUnauthenticated(fn) { onUnauthenticated = fn }

async function request(path, { method = 'GET', body, auth = true, isRetry = false } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['X-CSRF-Token'] = '1' // presence is what the filter checks
  if (auth && accessToken) headers['Authorization'] = `Bearer ${accessToken}`

  const res = await fetch(BASE + path, {
    method,
    headers,
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  // One transparent refresh+retry on a 401 for an authenticated call.
  if (res.status === 401 && auth && !isRetry) {
    const ok = await refreshSession()
    if (ok) return request(path, { method, body, auth, isRetry: true })
    accessToken = null
    onUnauthenticated()
  }
  return res
}

async function parse(res) {
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    const err = new Error(data?.error?.message || data?.message || `Request failed (${res.status})`)
    err.status = res.status
    err.code = data?.error?.code
    throw err
  }
  return data
}

/** Exchange the refresh cookie for a fresh access token. Returns true on success. */
export async function refreshSession() {
  try {
    const res = await fetch(BASE + '/auth/refresh', {
      method: 'POST',
      headers: { 'X-CSRF-Token': '1' },
      credentials: 'include',
    })
    if (!res.ok) return false
    const data = await res.json()
    accessToken = data.accessToken
    return true
  } catch {
    return false
  }
}

export const api = {
  get: (path) => request(path).then(parse),
  post: (path, body, opts) => request(path, { method: 'POST', body, ...opts }).then(parse),
  // health is public; skip auth and the refresh dance.
  health: () => fetch('/actuator/health').then((r) => r.json()),
}
