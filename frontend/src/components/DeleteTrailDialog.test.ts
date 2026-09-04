import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DeleteTrailDialog from './DeleteTrailDialog.vue'
import type { Trail } from '../types'

const deleteTrail = vi.fn()
const archiveTrail = vi.fn()
const getTrailCascade = vi.fn()

vi.mock('../api/trails', () => ({
  deleteTrail: (...a: unknown[]) => deleteTrail(...a),
  archiveTrail: (...a: unknown[]) => archiveTrail(...a),
  getTrailCascade: (...a: unknown[]) => getTrailCascade(...a),
}))

const trail = (overrides: Partial<Trail> = {}): Trail => ({
  id: 'trail-1',
  flowId: 'flow-1',
  gitCommitSha: 'abc1234567',
  gitBranch: 'main',
  gitAuthor: 'alice',
  gitAuthorEmail: 'alice@example.com',
  status: 'PENDING',
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  ...overrides,
})

const cascade = {
  attestations: 3,
  artifacts: 1,
  evidenceFiles: 2,
  approvals: 0,
  coverageReports: 0,
  securityScans: 0,
  complianceAssessments: 0,
  jiraTickets: 0,
  total: 6,
}

const mountDialog = (t = trail()) => mount(DeleteTrailDialog, { props: { trail: t } })

describe('DeleteTrailDialog', () => {
  beforeEach(() => {
    deleteTrail.mockReset().mockResolvedValue({ data: { trailId: 'trail-1', cascade } })
    archiveTrail.mockReset().mockResolvedValue({ data: trail({ archivedAt: '2026-02-01T10:00:00Z' }) })
    getTrailCascade.mockReset().mockResolvedValue({ data: cascade })
  })

  it('states exactly what deletion would remove', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const summary = wrapper.get('[data-test="cascade-summary"]').text()
    expect(summary).toContain('3')
    expect(summary.toLowerCase()).toContain('attestation')
    expect(summary).toContain('2')
    expect(summary.toLowerCase()).toContain('evidence')
  })

  it('keeps delete disabled until the trail id is typed exactly', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const confirm = wrapper.get('[data-test="confirm-delete"]')
    expect(confirm.attributes('disabled')).toBeDefined()

    await wrapper.get('[data-test="confirm-input"]').setValue('trail-')
    expect(wrapper.get('[data-test="confirm-delete"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-test="confirm-input"]').setValue('trail-1')
    expect(wrapper.get('[data-test="confirm-delete"]').attributes('disabled')).toBeUndefined()
  })

  it('deletes only once the confirmation matches', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.get('[data-test="confirm-input"]').setValue('trail-1')
    await wrapper.get('[data-test="confirm-delete"]').trigger('click')
    await flushPromises()

    expect(deleteTrail).toHaveBeenCalledWith('trail-1')
    expect(wrapper.emitted('deleted')?.[0]).toEqual(['trail-1'])
  })

  it('offers archiving as the safe alternative, with no confirmation needed', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.get('[data-test="archive-instead"]').trigger('click')
    await flushPromises()

    expect(archiveTrail).toHaveBeenCalledWith('trail-1')
    expect(deleteTrail).not.toHaveBeenCalled()
    expect(wrapper.emitted('archived')).toBeTruthy()
  })

  it('surfaces a failure rather than closing silently', async () => {
    deleteTrail.mockRejectedValue(new Error('500'))
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.get('[data-test="confirm-input"]').setValue('trail-1')
    await wrapper.get('[data-test="confirm-delete"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="delete-error"]').text()).toBeTruthy()
    expect(wrapper.emitted('deleted')).toBeUndefined()
  })

  it('emits cancel without touching anything', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.get('[data-test="cancel-delete"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(deleteTrail).not.toHaveBeenCalled()
    expect(archiveTrail).not.toHaveBeenCalled()
  })

  it('says that the audit log survives the deletion', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.text().toLowerCase()).toContain('audit')
  })
})
