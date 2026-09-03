import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EvidenceCell from './EvidenceCell.vue'
import type { Attestation } from '../types'

// #159: evidence must be reachable from the Trail view for all four cases.
const attestation = (overrides: Partial<Attestation> = {}): Attestation => ({
  id: 'att-1',
  trailId: 'trail-1',
  type: 'junit',
  status: 'PASSED',
  createdAt: '2026-01-01T10:00:00Z',
  ...overrides,
})

describe('EvidenceCell', () => {
  it('shows a placeholder when there is no evidence', () => {
    const wrapper = mount(EvidenceCell, { props: { attestation: attestation() } })
    expect(wrapper.text()).toContain('—')
    expect(wrapper.findAll('a')).toHaveLength(0)
  })

  it('links an uploaded file to the evidence download endpoint', () => {
    const wrapper = mount(EvidenceCell, {
      props: {
        attestation: attestation({
          evidenceFileName: 'junit-report.xml',
          evidenceFileHash: 'abc123',
          evidenceFileSizeBytes: 2048,
        }),
      },
    })
    const link = wrapper.get('a[data-test="evidence-file"]')
    expect(link.attributes('href')).toBe('/api/v1/evidence/abc123')
    expect(link.text()).toContain('junit-report.xml')
    expect(wrapper.text()).toContain('2 KB')
  })

  it('renders evidenceUrl as an external link that opens in a new tab', () => {
    const wrapper = mount(EvidenceCell, {
      props: { attestation: attestation({ evidenceUrl: 'https://ci.example.com/run/42' }) },
    })
    const link = wrapper.get('a[data-test="evidence-url"]')
    expect(link.attributes('href')).toBe('https://ci.example.com/run/42')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
    expect(link.text()).toContain('ci.example.com')
  })

  it('renders every external URL, not just the first', () => {
    const wrapper = mount(EvidenceCell, {
      props: {
        attestation: attestation({
          externalUrls: ['https://a.example.com/1', 'https://b.example.com/2'],
        }),
      },
    })
    expect(wrapper.findAll('a[data-test="evidence-url"]')).toHaveLength(2)
  })

  it('shows an uploaded file and links together', () => {
    const wrapper = mount(EvidenceCell, {
      props: {
        attestation: attestation({
          evidenceFileName: 'scan.json',
          evidenceFileHash: 'def456',
          evidenceFileSizeBytes: 100,
          evidenceUrl: 'https://ci.example.com/run/42',
        }),
      },
    })
    expect(wrapper.findAll('a[data-test="evidence-file"]')).toHaveLength(1)
    expect(wrapper.findAll('a[data-test="evidence-url"]')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('—')
  })

  it('shows the truncated evidence hash so integrity is verifiable', () => {
    const wrapper = mount(EvidenceCell, {
      props: {
        attestation: attestation({
          evidenceFileName: 'scan.json',
          evidenceFileHash: '0123456789abcdef0123456789abcdef',
        }),
      },
    })
    const hash = wrapper.get('[data-test="evidence-hash"]')
    expect(hash.text()).toContain('0123456789ab')
    expect(hash.attributes('title')).toBe('0123456789abcdef0123456789abcdef')
  })

  it('falls back to the hash as the link label when no filename was stored', () => {
    const wrapper = mount(EvidenceCell, {
      props: { attestation: attestation({ evidenceFileHash: 'abc123' }) },
    })
    const link = wrapper.get('a[data-test="evidence-file"]')
    expect(link.attributes('href')).toBe('/api/v1/evidence/abc123')
    expect(wrapper.text()).not.toContain('—')
  })

  it('does not linkify a filename with no hash to download', () => {
    const wrapper = mount(EvidenceCell, {
      props: { attestation: attestation({ evidenceFileName: 'orphan.xml' }) },
    })
    expect(wrapper.findAll('a[data-test="evidence-file"]')).toHaveLength(0)
    expect(wrapper.text()).toContain('orphan.xml')
  })
})
