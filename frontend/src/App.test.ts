import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import App from './App.vue'
import { setAppError, clearAppError } from './composables/useAppError'

// #157: a render error must surface as a visible banner, never a white screen.
const mountApp = () =>
  mount(App, {
    global: {
      stubs: { NavBar: true, RouterView: true, RouterLink: true },
    },
  })

describe('App error banner', () => {
  beforeEach(() => clearAppError())

  it('shows nothing while there is no error', () => {
    expect(mountApp().find('[data-test="app-error"]').exists()).toBe(false)
  })

  it('renders the error reported by the global handler', async () => {
    const wrapper = mountApp()
    setAppError(new TypeError("Cannot read properties of undefined (reading 'length')"), 'Something went wrong')
    await wrapper.vm.$nextTick()

    const banner = wrapper.get('[data-test="app-error"]')
    expect(banner.text()).toContain('Something went wrong')
    expect(banner.text()).toContain("reading 'length'")
    expect(banner.attributes('role')).toBe('alert')
  })

  it('can be dismissed', async () => {
    const wrapper = mountApp()
    setAppError('boom')
    await wrapper.vm.$nextTick()

    await wrapper.get('[data-test="app-error"] button').trigger('click')
    expect(wrapper.find('[data-test="app-error"]').exists()).toBe(false)
  })
})
