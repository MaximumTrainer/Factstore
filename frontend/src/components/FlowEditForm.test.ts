import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FlowEditForm from './FlowEditForm.vue'
import type { Flow } from '../types'

const updateFlow = vi.fn()
const getFlowImpact = vi.fn()
const listAttestationTypes = vi.fn()

vi.mock('../api/flows', () => ({
  updateFlow: (...args: unknown[]) => updateFlow(...args),
  getFlowImpact: (...args: unknown[]) => getFlowImpact(...args),
}))

vi.mock('../api/attestationTypes', () => ({
  listAttestationTypes: (...args: unknown[]) => listAttestationTypes(...args),
}))

const flow = (overrides: Partial<Flow> = {}): Flow => ({
  id: 'flow-1',
  name: 'payments-api',
  description: 'Payments service gates',
  requiredAttestationTypes: ['junit', 'snyk'],
  tags: { team: 'payments' },
  requiresApproval: false,
  requiredApproverRoles: [],
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  ...overrides,
})

const mountForm = (f = flow()) =>
  mount(FlowEditForm, { props: { flow: f } })

// jsdom does not submit a form when a submit button is clicked, so drive the form the way
// the browser does. `the save button submits the form` guards that wiring.
type Wrapper = ReturnType<typeof mountForm>
const submitForm = (wrapper: Wrapper) => wrapper.get('form').trigger('submit')

describe('FlowEditForm', () => {
  beforeEach(() => {
    updateFlow.mockReset().mockResolvedValue({ data: flow() })
    getFlowImpact.mockReset().mockResolvedValue({
      data: { flowId: 'flow-1', flowName: 'payments-api', trailCount: 0, pendingTrailCount: 0 },
    })
    listAttestationTypes.mockReset().mockResolvedValue({ data: [] })
  })

  it('pre-populates every editable field from the current flow', async () => {
    const wrapper = mountForm()
    await flushPromises()

    expect((wrapper.get('[data-test="flow-name"]').element as HTMLInputElement).value).toBe('payments-api')
    expect((wrapper.get('[data-test="flow-description"]').element as HTMLTextAreaElement).value)
      .toBe('Payments service gates')
    expect((wrapper.get('[data-test="flow-attestation-types"]').element as HTMLInputElement).value)
      .toBe('junit, snyk')
    expect((wrapper.get('[data-test="flow-tags"]').element as HTMLInputElement).value).toBe('team=payments')
    expect((wrapper.get('[data-test="flow-requires-approval"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('sends the whole editable payload, including the fields the old client dropped', async () => {
    const wrapper = mountForm()
    await flushPromises()

    await wrapper.get('[data-test="flow-attestation-types"]').setValue('junit, snyk, ghas')
    await wrapper.get('[data-test="flow-requires-approval"]').setValue(true)
    await wrapper.get('[data-test="flow-approver-roles"]').setValue('release-manager')
    await wrapper.get('[data-test="flow-template-yaml"]').setValue('version: 1\n')
    await submitForm(wrapper)
    await flushPromises()

    expect(updateFlow).toHaveBeenCalledWith('flow-1', {
      name: 'payments-api',
      description: 'Payments service gates',
      requiredAttestationTypes: ['junit', 'snyk', 'ghas'],
      tags: { team: 'payments' },
      requiresApproval: true,
      requiredApproverRoles: ['release-manager'],
      templateYaml: 'version: 1\n',
    })
  })

  it('never sends a visibility field, which the backend does not have', async () => {
    const wrapper = mountForm()
    await flushPromises()

    await submitForm(wrapper)
    await flushPromises()

    expect(updateFlow.mock.calls[0][1]).not.toHaveProperty('visibility')
  })

  it('omits an empty template rather than blanking a template that was never set', async () => {
    const wrapper = mountForm()
    await flushPromises()

    await submitForm(wrapper)
    await flushPromises()

    expect(updateFlow.mock.calls[0][1]).not.toHaveProperty('templateYaml')
  })

  it('warns how many existing trails a change to the required gates affects', async () => {
    getFlowImpact.mockResolvedValue({
      data: { flowId: 'flow-1', flowName: 'payments-api', trailCount: 12, pendingTrailCount: 3 },
    })
    const wrapper = mountForm()
    await flushPromises()

    await wrapper.get('[data-test="flow-attestation-types"]').setValue('junit, snyk, ghas')
    await flushPromises()

    const warning = wrapper.get('[data-test="flow-impact-warning"]')
    expect(warning.text()).toContain('12')
    expect(warning.text().toLowerCase()).toContain('trail')
  })

  it('does not warn when the required gates are unchanged', async () => {
    getFlowImpact.mockResolvedValue({
      data: { flowId: 'flow-1', flowName: 'payments-api', trailCount: 12, pendingTrailCount: 3 },
    })
    const wrapper = mountForm()
    await flushPromises()

    await wrapper.get('[data-test="flow-description"]').setValue('a new description')
    await flushPromises()

    expect(wrapper.find('[data-test="flow-impact-warning"]').exists()).toBe(false)
  })

  it('offers the existing attestation types as suggestions', async () => {
    listAttestationTypes.mockResolvedValue({
      data: [
        { id: 't1', name: 'ghas', description: '', version: 1, createdAt: '', updatedAt: '' },
        { id: 't2', name: 'junit', description: '', version: 1, createdAt: '', updatedAt: '' },
      ],
    })
    const wrapper = mountForm()
    await flushPromises()

    const suggestions = wrapper.findAll('[data-test="attestation-type-suggestion"]')
    // junit is already required, so only ghas is offered.
    expect(suggestions).toHaveLength(1)
    expect(suggestions[0].text()).toContain('ghas')
  })

  it('adds a suggested type to the required list when clicked', async () => {
    listAttestationTypes.mockResolvedValue({
      data: [{ id: 't1', name: 'ghas', description: '', version: 1, createdAt: '', updatedAt: '' }],
    })
    const wrapper = mountForm()
    await flushPromises()

    await wrapper.get('[data-test="attestation-type-suggestion"]').trigger('click')

    expect((wrapper.get('[data-test="flow-attestation-types"]').element as HTMLInputElement).value)
      .toBe('junit, snyk, ghas')
  })

  it('emits saved with the updated flow so the parent can refresh', async () => {
    const updated = flow({ description: 'changed' })
    updateFlow.mockResolvedValue({ data: updated })
    const wrapper = mountForm()
    await flushPromises()

    await submitForm(wrapper)
    await flushPromises()

    expect(wrapper.emitted('saved')?.[0]).toEqual([updated])
  })

  it('surfaces a save failure instead of closing silently', async () => {
    updateFlow.mockRejectedValue(new Error('409'))
    const wrapper = mountForm()
    await flushPromises()

    await submitForm(wrapper)
    await flushPromises()

    expect(wrapper.get('[data-test="flow-error"]').text()).toBeTruthy()
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('wires the save button to submit the form', async () => {
    const wrapper = mountForm()
    await flushPromises()

    const save = wrapper.get('[data-test="flow-save"]')
    expect(save.attributes('type')).toBe('submit')
    expect(save.element.closest('form')).toBe(wrapper.get('form').element)
  })

  it('emits cancel without saving', async () => {
    const wrapper = mountForm()
    await flushPromises()

    await wrapper.get('[data-test="flow-cancel"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(updateFlow).not.toHaveBeenCalled()
  })
})
