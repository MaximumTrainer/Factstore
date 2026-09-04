import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GateActivityChart from './GateActivityChart.vue'
import type { GateDayBucket } from '../types'

const days = (): GateDayBucket[] => [
  { date: '2026-09-01', allowed: 4, blocked: 1 },
  { date: '2026-09-02', allowed: 0, blocked: 0 },
  { date: '2026-09-03', allowed: 2, blocked: 3 },
]

const mountChart = (perDay = days()) => mount(GateActivityChart, { props: { perDay } })

describe('GateActivityChart', () => {
  it('draws one stack per day, empty days included', () => {
    const wrapper = mountChart()

    expect(wrapper.findAll('[data-test="day-group"]')).toHaveLength(3)
  })

  it('draws a segment per non-zero value only', () => {
    const wrapper = mountChart()

    // day 1: allowed+blocked, day 2: nothing, day 3: allowed+blocked
    expect(wrapper.findAll('[data-test="seg-allowed"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-test="seg-blocked"]')).toHaveLength(2)
  })

  it('scales the tallest stack to the plot height', () => {
    const wrapper = mountChart([
      { date: '2026-09-01', allowed: 1, blocked: 0 },
      { date: '2026-09-02', allowed: 9, blocked: 1 },
    ])

    const heights = wrapper
      .findAll('[data-test="seg-allowed"]')
      .map(el => Number(el.attributes('height')))
    // The 9-tall bar must be substantially taller than the 1-tall bar.
    expect(heights[1]).toBeGreaterThan(heights[0] * 5)
  })

  // Identity must never rest on colour alone.
  it('names both series in a legend', () => {
    const wrapper = mountChart()

    const legend = wrapper.get('[data-test="chart-legend"]').text()
    expect(legend).toContain('Allowed')
    expect(legend).toContain('Blocked')
  })

  it('offers a table view of the same numbers', async () => {
    const wrapper = mountChart()

    await wrapper.get('[data-test="toggle-table"]').trigger('click')

    const table = wrapper.get('[data-test="chart-table"]')
    expect(table.text()).toContain('2026-09-01')
    expect(table.text()).toContain('4')
    expect(table.text()).toContain('3')
  })

  it('labels every segment for a screen reader and on hover', () => {
    const wrapper = mountChart()

    const first = wrapper.get('[data-test="seg-allowed"]')
    expect(first.attributes('aria-label')).toContain('4')
    expect(first.attributes('aria-label')?.toLowerCase()).toContain('allowed')
  })

  it('drops the legend and table toggle when there is nothing to describe', () => {
    const wrapper = mountChart([{ date: '2026-09-01', allowed: 0, blocked: 0 }])

    expect(wrapper.find('[data-test="chart-legend"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="toggle-table"]').exists()).toBe(false)
  })

  it('keeps a sparse window drawing thin marks rather than a wall of colour', () => {
    const wrapper = mountChart([
      { date: '2026-09-01', allowed: 1, blocked: 0 },
      { date: '2026-09-02', allowed: 0, blocked: 12 },
    ])

    const width = Number(wrapper.get('[data-test="seg-allowed"]').attributes('width'))
    expect(width).toBeLessThanOrEqual(26)
  })

  it('says so when there is no activity, rather than drawing an empty grid', () => {
    const wrapper = mountChart([
      { date: '2026-09-01', allowed: 0, blocked: 0 },
      { date: '2026-09-02', allowed: 0, blocked: 0 },
    ])

    expect(wrapper.text().toLowerCase()).toContain('no gate activity')
    expect(wrapper.findAll('[data-test="seg-allowed"]')).toHaveLength(0)
  })

  it('renders nothing but a message for an empty window', () => {
    const wrapper = mountChart([])

    expect(wrapper.text().toLowerCase()).toContain('no gate activity')
  })
})
