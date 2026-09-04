import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StatTile from './StatTile.vue'
import type { DoraMetric } from '../types'

const metric = (overrides: Partial<DoraMetric> = {}): DoraMetric => ({
  value: 2.5,
  unit: 'per day',
  basis: 'Deployments recorded to any environment, divided by the days in the window.',
  sampleSize: 75,
  available: true,
  ...overrides,
})

describe('StatTile', () => {
  it('shows the label and the value with its unit', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Deployment frequency', metric: metric() },
    })

    expect(wrapper.text()).toContain('Deployment frequency')
    expect(wrapper.get('[data-test="stat-value"]').text()).toBe('2.5')
    expect(wrapper.text()).toContain('per day')
  })

  it('rounds a long decimal rather than printing every digit', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Lead time', metric: metric({ value: 13.333333, unit: 'hours' }) },
    })

    expect(wrapper.get('[data-test="stat-value"]').text()).toBe('13.3')
  })

  it('compacts a large value', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Deployments', metric: metric({ value: 12900, unit: 'total' }) },
    })

    expect(wrapper.get('[data-test="stat-value"]').text()).toBe('12.9K')
  })

  it('prints a percentage without inventing precision', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Block rate', metric: metric({ value: 25, unit: 'percent' }) },
    })

    expect(wrapper.get('[data-test="stat-value"]').text()).toBe('25%')
  })

  it('states the sample size, so a number from three data points is not read as a trend', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Lead time', metric: metric({ sampleSize: 3 }) },
    })

    expect(wrapper.get('[data-test="stat-sample"]').text()).toContain('3')
  })

  // The point of the tile: an unavailable metric must never look like a good result.
  it('shows an unavailable metric as unavailable, not as zero', () => {
    const wrapper = mount(StatTile, {
      props: {
        label: 'Time to restore service',
        metric: metric({ value: null, available: false, sampleSize: 0, basis: 'No incident records.' }),
      },
    })

    expect(wrapper.get('[data-test="stat-value"]').text()).not.toContain('0')
    expect(wrapper.get('[data-test="stat-value"]').text()).toBe('—')
    expect(wrapper.text()).toContain('Not available')
  })

  it('explains why an unavailable metric is unavailable', () => {
    const wrapper = mount(StatTile, {
      props: {
        label: 'Time to restore service',
        metric: metric({ value: null, available: false, basis: 'No incident records are kept here.' }),
      },
    })

    expect(wrapper.get('[data-test="stat-basis"]').text()).toContain('No incident records')
  })

  it('always exposes what the number actually measures', () => {
    const wrapper = mount(StatTile, { props: { label: 'Lead time', metric: metric() } })

    expect(wrapper.get('[data-test="stat-basis"]').text()).toContain('Deployments recorded')
  })
})
