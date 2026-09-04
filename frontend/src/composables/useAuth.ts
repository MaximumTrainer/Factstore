import { computed, readonly, ref } from 'vue'
import {
  getMe,
  logout as logoutRequest,
  switchOrganisation as switchOrganisationRequest,
  type AuthenticatedPrincipal,
} from '../api/auth'

/**
 * The signed-in principal, shared across the app (#156 FR-4.4, FR-6.4).
 *
 * Every navigation and control-visibility decision reads from here, and the contents come
 * from `/auth/me` — so the server decides what someone may do and the UI reflects it, rather
 * than the two making the decision separately and disagreeing.
 *
 * The UI is never the only check: hiding a control the user cannot use is a courtesy, and
 * the server refuses the request regardless.
 */
const principal = ref<AuthenticatedPrincipal | null>(null)
const loading = ref(false)
/** Null until the first `/auth/me` has settled, so a guard can wait rather than guess. */
const resolved = ref(false)

let inFlight: Promise<AuthenticatedPrincipal | null> | null = null

/**
 * Loads the principal, de-duplicating concurrent callers — several route guards and the
 * navbar all ask at once on a cold load.
 */
export async function loadPrincipal(force = false): Promise<AuthenticatedPrincipal | null> {
  if (!force && resolved.value) return principal.value
  if (inFlight) return inFlight

  loading.value = true
  inFlight = getMe()
    .then(({ data }) => {
      principal.value = data
      return data
    })
    .catch(() => {
      // A 401 here is the normal "not signed in" answer, not an error worth surfacing.
      principal.value = null
      return null
    })
    .finally(() => {
      resolved.value = true
      loading.value = false
      inFlight = null
    })

  return inFlight
}

/** Clears local state. Called by the 401 interceptor and by sign-out. */
export function clearPrincipal(): void {
  principal.value = null
  resolved.value = true
}

export async function signOut(): Promise<void> {
  try {
    await logoutRequest()
  } finally {
    // Clear regardless: if the call failed, the local session is still no longer wanted.
    clearPrincipal()
  }
}

export async function setActiveOrganisation(orgSlug: string): Promise<void> {
  const { data } = await switchOrganisationRequest(orgSlug)
  principal.value = data
}

export function useAuth() {
  const isAuthenticated = computed(() => principal.value != null)

  /** True when the principal holds this `resource:action` permission. */
  const can = (permission: string): boolean =>
    principal.value?.permissions?.includes(permission) ?? false

  const isAdmin = computed(() => can('admin'))

  const displayName = computed(
    () => principal.value?.name || principal.value?.email || principal.value?.ownerId || ''
  )

  return {
    principal: readonly(principal),
    loading: readonly(loading),
    resolved: readonly(resolved),
    isAuthenticated,
    isAdmin,
    can,
    displayName,
    loadPrincipal,
    clearPrincipal,
    signOut,
    setActiveOrganisation,
  }
}
