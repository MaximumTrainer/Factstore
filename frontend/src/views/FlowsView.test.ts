import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FlowsView from './FlowsView.vue'
import type { Flow } from '../types'

const getFlows = vi.fn()
const createFlow = vi.fn()
const archiveFlow = vi.fn()
const unarchiveFlow = vi.fn()

vi.mock('../api/flows', () => ({
  getFlows: (...a: unknown[]) => getFlows(...a),
  createFlow: (...a: unknown[]) => createFlow(...a),
  archiveFlow: (...a: unknown[]) => archiveFlow(...a),
  unarchiveFlow: (...a: unknown[]) => unarchiveFlow(...a),
}))

const flow = (overrides: Partial<Flow> = {}): Flow => ({
  id: 'flow-1',
  name: 'payments-api',
  description: 'gates',
  requiredAttestationTypes: ['junit'],
  tags: {},
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  ...overrides,
})

const mountView = () =>
  mount(FlowsView, {
    global: { stubs: { RouterLink: true, FlowEditForm: true } },
  })

describe('FlowsView', () => {
  beforeEach(() => {
    getFlows.mockReset().mockResolvedValue({ data: [flow()] })
    archiveFlow.mockReset().mockResolvedValue({ data: flow({ archivedAt: '2026-02-01T10:00:00Z' }) })
    unarchiveFlow.mockReset().mockResolvedValue({ data: flow({ archivedAt: null }) })
    createFlow.mockReset()
  })

  it('offers an edit action per row', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-test="edit-flow-row"]').exists()).toBe(true)
  })

  it('opens the edit form for the row that was clicked', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="edit-flow-row"]').trigger('click')

    expect(wrapper.findComponent({ name: 'FlowEditForm' }).exists()).toBe(true)
  })

  it('archives a flow and drops it from the default list', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="archive-flow-row"]').trigger('click')
    await flushPromises()

    expect(archiveFlow).toHaveBeenCalledWith('flow-1')
    expect(wrapper.text()).toContain('No flows found')
  })

  it('unarchives an archived flow', async () => {
    getFlows.mockResolvedValue({ data: [flow({ archivedAt: '2026-02-01T10:00:00Z' })] })
    const wrapper = mountView()
    await flushPromises()

    const action = wrapper.get('[data-test="archive-flow-row"]')
    expect(action.text()).toBe('Unarchive')
    await action.trigger('click')
    await flushPromises()

    expect(unarchiveFlow).toHaveBeenCalledWith('flow-1')
  })

  it('shows archived flows when asked to', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="include-archived"]').setValue(true)
    await flushPromises()

    expect(getFlows).toHaveBeenLastCalledWith(true)
  })

  it('marks an archived flow in the list', async () => {
    getFlows.mockResolvedValue({ data: [flow({ archivedAt: '2026-02-01T10:00:00Z' })] })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Archived')
  })
})
