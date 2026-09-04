import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { AuthenticatedPrincipal } from '../api/auth'

const getMe = vi.fn()
const logoutRequest = vi.fn()
const switchOrganisationRequest = vi.fn()

vi.mock('../api/auth', () => ({
  getMe: () => getMe(),
  logout: () => logoutRequest(),
  switchOrganisation: (...a: unknown[]) => switchOrganisationRequest(...a),
}))

const principal = (overrides: Partial<AuthenticatedPrincipal> = {}): AuthenticatedPrincipal => ({
  type: 'USER',
  userId: 'user-1',
  email: 'alice@example.com',
  name: 'Alice',
  orgSlug: 'acme',
  role: 'MEMBER',
  permissions: ['flows:read', 'trails:read', 'trails:write', 'attestations:write'],
  organisations: [{ orgSlug: 'acme', role: 'MEMBER' }],
  ...overrides,
})

import { clearPrincipal, loadPrincipal, useAuth } from './useAuth'

// The composable is deliberately module-scoped shared state, so each test resets it through
// the module's own API and loads with force. (Resetting the module registry instead gives a
// fresh instance only when nothing still holds the old one, which is easy to get wrong.)
const load = () => loadPrincipal(true)

describe('useAuth', () => {
  beforeEach(() => {
    getMe.mockReset().mockResolvedValue({ data: principal() })
    logoutRequest.mockReset().mockResolvedValue(undefined)
    switchOrganisationRequest.mockReset()
    clearPrincipal()
  })

  it('loads the principal and reports authenticated', async () => {
    await load()

    const { isAuthenticated, displayName } = useAuth()
    expect(isAuthenticated.value).toBe(true)
    expect(displayName.value).toBe('Alice')
  })

  it('treats a 401 as not signed in, not as an error', async () => {
    getMe.mockRejectedValue({ response: { status: 401 } })

    const result = await load()

    expect(result).toBeNull()
    expect(useAuth().isAuthenticated.value).toBe(false)
  })

  it('de-duplicates concurrent loads, so a cold page does not stampede', async () => {
    await Promise.all([loadPrincipal(true), loadPrincipal(true), loadPrincipal(true)])

    expect(getMe).toHaveBeenCalledTimes(1)
  })

  it('does not re-ask once resolved, unless forced', async () => {
    await loadPrincipal(true)
    await loadPrincipal()
    await loadPrincipal()
    expect(getMe).toHaveBeenCalledTimes(1)

    await loadPrincipal(true)
    expect(getMe).toHaveBeenCalledTimes(2)
  })

  it('answers permission questions from the server response', async () => {
    await load()

    const { can, isAdmin } = useAuth()
    expect(can('attestations:write')).toBe(true)
    expect(can('flows:write')).toBe(false)
    expect(isAdmin.value).toBe(false)
  })

  it('recognises an administrator', async () => {
    getMe.mockResolvedValue({
      data: principal({ role: 'ADMIN', permissions: ['flows:write', 'admin'] }),
    })
    await load()

    expect(useAuth().isAdmin.value).toBe(true)
    expect(useAuth().can('flows:write')).toBe(true)
  })

  it('grants nothing when unauthenticated', async () => {
    getMe.mockRejectedValue({ response: { status: 401 } })
    await load()

    expect(useAuth().can('flows:read')).toBe(false)
    expect(useAuth().isAdmin.value).toBe(false)
  })

  it('sign-out clears the principal', async () => {
    await load()
    expect(useAuth().isAuthenticated.value).toBe(true)

    await useAuth().signOut()

    expect(logoutRequest).toHaveBeenCalled()
    expect(useAuth().isAuthenticated.value).toBe(false)
  })

  it('sign-out clears locally even when the request fails', async () => {
    logoutRequest.mockRejectedValue(new Error('offline'))
    await load()

    await expect(useAuth().signOut()).rejects.toThrow()

    // The local session is no longer wanted regardless of what the server said.
    expect(useAuth().isAuthenticated.value).toBe(false)
  })

  it('switching organisation replaces the principal with the new scope', async () => {
    switchOrganisationRequest.mockResolvedValue({
      data: principal({ orgSlug: 'other', role: 'VIEWER', permissions: ['flows:read'] }),
    })
    await load()

    await useAuth().setActiveOrganisation('other')

    expect(switchOrganisationRequest).toHaveBeenCalledWith('other')
    expect(useAuth().principal.value?.orgSlug).toBe('other')
    expect(useAuth().can('trails:write')).toBe(false)
  })

  it('reports an API key principal by its owner', async () => {
    getMe.mockResolvedValue({
      data: {
        type: 'API_KEY',
        ownerId: 'owner-9',
        permissions: ['attestations:write'],
        organisations: [],
      } as AuthenticatedPrincipal,
    })
    await load()

    expect(useAuth().isAuthenticated.value).toBe(true)
    expect(useAuth().displayName.value).toBe('owner-9')
    expect(useAuth().can('attestations:write')).toBe(true)
  })
})
