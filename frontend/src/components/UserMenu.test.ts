import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import UserMenu from './UserMenu.vue'
import type { AuthenticatedPrincipal } from '../api/auth'

const signOut = vi.fn()
const setActiveOrganisation = vi.fn()
const push = vi.fn()
const go = vi.fn()

const principalRef = ref<AuthenticatedPrincipal | null>(null)

vi.mock('../composables/useAuth', () => ({
  useAuth: () => ({
    principal: principalRef,
    displayName: { value: principalRef.value?.name ?? '' },
    signOut,
    setActiveOrganisation,
  }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push, go }),
}))

const principal = (overrides: Partial<AuthenticatedPrincipal> = {}): AuthenticatedPrincipal => ({
  type: 'USER',
  userId: 'user-1',
  email: 'alice@example.com',
  name: 'Alice Smith',
  orgSlug: 'acme',
  role: 'MEMBER',
  permissions: ['flows:read', 'trails:write'],
  organisations: [
    { orgSlug: 'acme', role: 'MEMBER' },
    { orgSlug: 'other-org', role: 'VIEWER' },
  ],
  ...overrides,
})

describe('UserMenu', () => {
  beforeEach(() => {
    signOut.mockReset().mockResolvedValue(undefined)
    setActiveOrganisation.mockReset().mockResolvedValue(undefined)
    push.mockReset()
    go.mockReset()
    principalRef.value = principal()
  })

  it('renders nothing when nobody is signed in', () => {
    principalRef.value = null

    expect(mount(UserMenu).find('[data-test="user-menu"]').exists()).toBe(false)
  })

  it('shows who is signed in and their role', () => {
    const wrapper = mount(UserMenu)

    expect(wrapper.text()).toContain('Alice Smith')
    expect(wrapper.get('[data-test="user-role"]').text()).toBe('MEMBER')
  })

  it('the panel is closed until asked for', async () => {
    const wrapper = mount(UserMenu)

    expect(wrapper.find('[data-test="user-menu-panel"]').exists()).toBe(false)
    await wrapper.get('[data-test="user-menu-button"]').trigger('click')
    expect(wrapper.find('[data-test="user-menu-panel"]').exists()).toBe(true)
  })

  it('lists every organisation the user belongs to', async () => {
    const wrapper = mount(UserMenu)
    await wrapper.get('[data-test="user-menu-button"]').trigger('click')

    const options = wrapper.findAll('[data-test="org-option"]')
    expect(options).toHaveLength(2)
    expect(options[0].text()).toContain('acme')
    expect(options[1].text()).toContain('other-org')
  })

  it('switches organisation and reloads, so stale data is not shown under a new name', async () => {
    const wrapper = mount(UserMenu)
    await wrapper.get('[data-test="user-menu-button"]').trigger('click')

    await wrapper.findAll('[data-test="org-option"]')[1].trigger('click')
    await flushPromises()

    expect(setActiveOrganisation).toHaveBeenCalledWith('other-org')
    expect(go).toHaveBeenCalledWith(0)
  })

  it('selecting the current organisation just closes the menu', async () => {
    const wrapper = mount(UserMenu)
    await wrapper.get('[data-test="user-menu-button"]').trigger('click')

    await wrapper.findAll('[data-test="org-option"]')[0].trigger('click')
    await flushPromises()

    expect(setActiveOrganisation).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="user-menu-panel"]').exists()).toBe(false)
  })

  it('surfaces a failed switch rather than silently staying put', async () => {
    setActiveOrganisation.mockRejectedValue(new Error('403'))
    const wrapper = mount(UserMenu)
    await wrapper.get('[data-test="user-menu-button"]').trigger('click')

    await wrapper.findAll('[data-test="org-option"]')[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Could not switch organisation')
    expect(go).not.toHaveBeenCalled()
  })

  it('shows the permissions the session actually holds', async () => {
    const wrapper = mount(UserMenu)
    await wrapper.get('[data-test="user-menu-button"]').trigger('click')

    expect(wrapper.text()).toContain('trails:write')
    expect(wrapper.text()).not.toContain('flows:write')
  })

  it('signs out and returns to the login page', async () => {
    const wrapper = mount(UserMenu)
    await wrapper.get('[data-test="user-menu-button"]').trigger('click')

    await wrapper.get('[data-test="sign-out"]').trigger('click')
    await flushPromises()

    expect(signOut).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/login')
  })
})
