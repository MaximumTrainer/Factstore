import { getAuthConfig } from '../api/auth'
import { loadPrincipal } from '../composables/useAuth'

/**
 * Whether the UI gates navigation, and on what (#156 FR-6.3).
 *
 * The rule: **the UI gates exactly when the server gates.** `security.enforce-auth` defaults
 * to `false` during the #155 rollout, and a UI that bounced every route to `/login` on an
 * instance that was not asking anyone to sign in made the default deployment unusable.
 *
 * The server is the authority on whether it is enforcing, so the client asks rather than
 * deciding separately — the same reason permissions come from `/auth/me` instead of being
 * inferred in the browser.
 */
export interface NavigationTarget {
  fullPath: string
  meta: { public?: boolean }
}

export type NavigationDecision =
  | true
  | { path: string; query: { redirect: string; reason: string } }

/** Asked once per page load: enforcement does not change under a running client. */
let enforcement: Promise<boolean> | null = null

export function enforcesAuth(): Promise<boolean> {
  enforcement ??= getAuthConfig()
    .then(({ data }) => data.enforceAuth)
    // An unreachable config endpoint is treated as enforcing. Failing open would show a
    // signed-out visitor a dashboard they may not be entitled to; failing closed shows them
    // a sign-in page they can leave.
    .catch(() => true)
  return enforcement
}

/** Test seam: forget the cached answer. */
export function resetAuthConfigCache(): void {
  enforcement = null
}

export async function resolveNavigation(to: NavigationTarget): Promise<NavigationDecision> {
  if (to.meta.public) return true

  if (!(await enforcesAuth())) return true

  if (await loadPrincipal()) return true

  // The intended destination is preserved so sign-in lands where the user was going, rather
  // than dumping them on the dashboard.
  return {
    path: '/login',
    query: {
      redirect: to.fullPath,
      reason: 'unauthenticated',
    },
  }
}
