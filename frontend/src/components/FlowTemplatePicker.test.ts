import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FlowTemplatePicker from './FlowTemplatePicker.vue'

const listTemplates = vi.fn()
const composeTemplates = vi.fn()

vi.mock('../api/hub', () => ({
  listTemplates: (...a: unknown[]) => listTemplates(...a),
  composeTemplates: (...a: unknown[]) => composeTemplates(...a),
}))

const serviceTypes = [
  {
    id: 'service-public-api',
    name: 'Public API Service',
    description: 'Widest blast radius',
    category: 'SERVICE_TYPE',
    serviceType: 'public-api',
    framework: 'house-standard',
    version: '1.0',
    yaml: 'version: 1\n',
  },
  {
    id: 'service-internal',
    name: 'Internal Microservice',
    description: 'Inside the estate',
    category: 'SERVICE_TYPE',
    serviceType: 'internal',
    framework: 'house-standard',
    version: '1.0',
    yaml: 'version: 1\n',
  },
]

const frameworks = [
  {
    id: 'pci-dss-v4',
    name: 'PCI-DSS v4',
    description: 'Payment card standard',
    category: 'FRAMEWORK',
    framework: 'PCI-DSS',
    version: '4.0',
    yaml: 'version: 1\n',
  },
]

describe('FlowTemplatePicker', () => {
  beforeEach(() => {
    listTemplates.mockReset().mockResolvedValue({ data: [...serviceTypes, ...frameworks] })
    composeTemplates.mockReset().mockResolvedValue({
      data: {
        templateIds: ['service-public-api'],
        templateYaml: 'version: 1\ntrail:\n  attestations:\n    - name: unit-tests\n',
        requiredAttestations: ['unit-tests', 'sast', 'api-tests'],
        conflicts: [],
      },
    })
  })

  const mountPicker = () => mount(FlowTemplatePicker)

  it('separates service types from regulatory frameworks', async () => {
    const wrapper = mountPicker()
    await flushPromises()

    expect(wrapper.findAll('[data-test="service-type-option"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-test="framework-option"]')).toHaveLength(1)
  })

  it('starts on a blank flow, requiring nothing', async () => {
    const wrapper = mountPicker()
    await flushPromises()

    expect(wrapper.find('[data-test="template-preview"]').exists()).toBe(false)
    expect(composeTemplates).not.toHaveBeenCalled()
  })

  it('previews the gates a chosen service type will require', async () => {
    const wrapper = mountPicker()
    await flushPromises()

    await wrapper.get('[data-test="service-type-option"]').trigger('click')
    await flushPromises()

    expect(composeTemplates).toHaveBeenCalledWith(['service-public-api'])
    const preview = wrapper.get('[data-test="template-preview"]').text()
    expect(preview).toContain('unit-tests')
    expect(preview).toContain('api-tests')
  })

  it('combines a service type with a regulatory framework', async () => {
    const wrapper = mountPicker()
    await flushPromises()

    await wrapper.get('[data-test="service-type-option"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="framework-option"]').trigger('click')
    await flushPromises()

    expect(composeTemplates).toHaveBeenLastCalledWith(['service-public-api', 'pci-dss-v4'])
  })

  it('only one service type at a time; picking another replaces it', async () => {
    const wrapper = mountPicker()
    await flushPromises()

    const options = wrapper.findAll('[data-test="service-type-option"]')
    await options[0].trigger('click')
    await flushPromises()
    await options[1].trigger('click')
    await flushPromises()

    expect(composeTemplates).toHaveBeenLastCalledWith(['service-internal'])
  })

  it('surfaces a conflict between the chosen templates', async () => {
    composeTemplates.mockResolvedValue({
      data: {
        templateIds: ['service-public-api', 'pci-dss-v4'],
        templateYaml: 'version: 1\n',
        requiredAttestations: ['unit-tests'],
        conflicts: ["'unit-tests' is required as type 'junit' and as type 'pytest'"],
      },
    })
    const wrapper = mountPicker()
    await flushPromises()

    await wrapper.get('[data-test="service-type-option"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="template-conflicts"]').text()).toContain('unit-tests')
  })

  it('emits the composed selection so the create form can save it', async () => {
    const wrapper = mountPicker()
    await flushPromises()

    await wrapper.get('[data-test="service-type-option"]').trigger('click')
    await flushPromises()

    const emitted = wrapper.emitted('change')
    expect(emitted).toBeTruthy()
    const payload = emitted![emitted!.length - 1][0] as {
      templateIds: string[]
      requiredAttestations: string[]
    }
    expect(payload.templateIds).toEqual(['service-public-api'])
    expect(payload.requiredAttestations).toContain('api-tests')
  })

  it('returning to a blank flow clears the selection', async () => {
    const wrapper = mountPicker()
    await flushPromises()

    await wrapper.get('[data-test="service-type-option"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="blank-flow"]').trigger('click')
    await flushPromises()

    const emitted = wrapper.emitted('change')!
    expect((emitted[emitted.length - 1][0] as { templateIds: string[] }).templateIds).toEqual([])
    expect(wrapper.find('[data-test="template-preview"]').exists()).toBe(false)
  })

  it('still lets a flow be created when the catalogue cannot be loaded', async () => {
    listTemplates.mockRejectedValue(new Error('offline'))
    const wrapper = mountPicker()
    await flushPromises()

    expect(wrapper.findAll('[data-test="service-type-option"]')).toHaveLength(0)
    expect(wrapper.find('[data-test="blank-flow"]').exists()).toBe(true)
  })
})
