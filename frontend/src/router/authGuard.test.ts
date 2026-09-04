import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

/**
 * Whether the UI gates navigation (#156 FR-6.3).
 *
 * The defect this pins down: the guard redirected to `/login` whenever `/auth/me` returned
 * no principal — and `security.enforce-auth` deliberately defaults to `false`. So the default
 * deployment's UI was unreachable: every route bounced to a sign-in page, on an instance that
 * was not asking anyone to sign in. Every E2E test failed for this reason, which is how it
 * was found, but the tests were only the messenger.
 *
 * The rule is that the UI gates exactly when the server gates. The server is the authority on
 * that, so the client asks it rather than deciding separately.
 */
describe('the navigation guard', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const load = async (opts: {
    enforceAuth: boolean
    principal: unknown | null
  }) => {
    vi.doMock('../api/auth', () => ({
      getAuthConfig: vi.fn().mockResolvedValue({ data: { enforceAuth: opts.enforceAuth } }),
      getMe: vi.fn().mockResolvedValue({ data: opts.principal }),
    }))
    vi.doMock('../composables/useAuth', () => ({
      loadPrincipal: vi.fn().mockResolvedValue(opts.principal),
      clearPrincipal: vi.fn(),
    }))
    const mod = await import('./authGuard')
    return mod
  }

  it('lets an anonymous visitor through when the server is not enforcing auth', async () => {
    const { resolveNavigation } = await load({ enforceAuth: false, principal: null })

    expect(await resolveNavigation({ fullPath: '/flows', meta: {} })).toBe(true)
  })

  it('redirects an anonymous visitor to login when the server is enforcing auth', async () => {
    const { resolveNavigation } = await load({ enforceAuth: true, principal: null })

    const result = await resolveNavigation({ fullPath: '/flows', meta: {} })

    expect(result).toEqual({
      path: '/login',
      query: { redirect: '/flows', reason: 'unauthenticated' },
    })
  })

  it('lets a signed-in user through when the server is enforcing auth', async () => {
    const { resolveNavigation } = await load({
      enforceAuth: true,
      principal: { type: 'USER', permissions: [], organisations: [] },
    })

    expect(await resolveNavigation({ fullPath: '/flows', meta: {} })).toBe(true)
  })

  it('always lets a public route through, so /login itself is reachable', async () => {
    const { resolveNavigation } = await load({ enforceAuth: true, principal: null })

    expect(await resolveNavigation({ fullPath: '/login', meta: { public: true } })).toBe(true)
  })

  it('treats an unreachable config endpoint as enforcing, which is the safe answer', async () => {
    vi.doMock('../api/auth', () => ({
      getAuthConfig: vi.fn().mockRejectedValue(new Error('network')),
      getMe: vi.fn().mockResolvedValue({ data: null }),
    }))
    vi.doMock('../composables/useAuth', () => ({
      loadPrincipal: vi.fn().mockResolvedValue(null),
      clearPrincipal: vi.fn(),
    }))
    const { resolveNavigation } = await import('./authGuard')

    // Failing open would show a signed-out visitor a dashboard they may not be entitled to.
    expect(await resolveNavigation({ fullPath: '/flows', meta: {} })).toEqual({
      path: '/login',
      query: { redirect: '/flows', reason: 'unauthenticated' },
    })
  })

  it('asks the server once, not on every navigation', async () => {
    const getAuthConfig = vi.fn().mockResolvedValue({ data: { enforceAuth: false } })
    vi.doMock('../api/auth', () => ({ getAuthConfig, getMe: vi.fn() }))
    vi.doMock('../composables/useAuth', () => ({
      loadPrincipal: vi.fn().mockResolvedValue(null),
      clearPrincipal: vi.fn(),
    }))
    const { resolveNavigation } = await import('./authGuard')

    await resolveNavigation({ fullPath: '/flows', meta: {} })
    await resolveNavigation({ fullPath: '/trails', meta: {} })

    expect(getAuthConfig).toHaveBeenCalledTimes(1)
  })
})
