import axios from 'axios'

/**
 * The API client (#156 FR-6.1).
 *
 * Two things happen here that did not before:
 *
 *  - **The session credential travels.** It is an `HttpOnly` cookie, so JavaScript cannot read
 *    it and `withCredentials` is what makes the browser send it. `X-Factstore-Client` marks the
 *    request as coming from this app; the server only honours a cookie on a mutating request
 *    that carries it, which is what closes CSRF without a token exchange.
 *  - **A 401 is handled.** It clears the local principal and redirects to `/login`, preserving
 *    where the user was going. Previously an expired session produced an unhandled rejection
 *    and, on a render path, a blank page.
 */
const client = axios.create({
  baseURL: '/api/v1',
  // The session cookie is HttpOnly; the browser attaches it, we cannot.
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'X-Factstore-Client': 'web',
  },
})

/** Set by the router so the interceptor can redirect without importing it (circular). */
type Redirector = (path: string) => void
let redirect: Redirector | null = null
let onUnauthenticated: (() => void) | null = null

export function configureAuthHandling(options: {
  redirect: Redirector
  onUnauthenticated: () => void
}): void {
  redirect = options.redirect
  onUnauthenticated = options.onUnauthenticated
}

/** Requests that a 401 must not bounce, because 401 is their normal answer. */
const SILENT_401_PATHS = ['/auth/me', '/auth/refresh', '/auth/logout']

client.interceptors.response.use(
  response => response,
  error => {
    const status = error?.response?.status
    const url: string = error?.config?.url ?? ''

    if (status === 401 && !SILENT_401_PATHS.some(path => url.startsWith(path))) {
      onUnauthenticated?.()
      const current = window.location.pathname + window.location.search
      if (!current.startsWith('/login')) {
        redirect?.(`/login?redirect=${encodeURIComponent(current)}`)
      }
    }

    // 403 is not a session problem: the caller is signed in and simply may not do this.
    // It is surfaced by the view, not by bouncing to /login.
    return Promise.reject(error)
  }
)

export default client
