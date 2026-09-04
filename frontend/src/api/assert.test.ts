import { describe, it, expect } from 'vitest'
import { toAssertResult } from './assert'
import type { AssertResponse } from '../types'

// #157: the server returns status/details/*AttestationTypes/*AttestationNames.
// Nothing may reach a template as `undefined`.
const base: AssertResponse = {
  sha256Digest: 'sha256:abc',
  flowId: 'flow-1',
  status: 'COMPLIANT',
  missingAttestationTypes: [],
  failedAttestationTypes: [],
  missingAttestationNames: [],
  failedAttestationNames: [],
  details: 'All required attestations passed',
  trailId: 'trail-1',
}

describe('toAssertResult', () => {
  it('maps a COMPLIANT response', () => {
    const result = toAssertResult(base)
    expect(result.compliant).toBe(true)
    expect(result.status).toBe('COMPLIANT')
    expect(result.message).toBe('All required attestations passed')
    expect(result.missingAttestations).toEqual([])
    expect(result.failedAttestations).toEqual([])
    expect(result.trailId).toBe('trail-1')
  })

  it('maps a NON_COMPLIANT legacy (requiredAttestationTypes) response', () => {
    const result = toAssertResult({
      ...base,
      status: 'NON_COMPLIANT',
      missingAttestationTypes: ['snyk'],
      failedAttestationTypes: ['junit'],
      details: 'Missing required attestations: snyk',
    })
    expect(result.compliant).toBe(false)
    expect(result.missingAttestations).toEqual(['snyk'])
    expect(result.failedAttestations).toEqual(['junit'])
  })

  it('maps a NON_COMPLIANT template-driven response', () => {
    const result = toAssertResult({
      ...base,
      status: 'NON_COMPLIANT',
      missingAttestationNames: ['security-scan'],
      failedAttestationNames: ['unit-tests'],
      details: 'Missing required attestations: security-scan',
    })
    expect(result.compliant).toBe(false)
    expect(result.missingAttestations).toEqual(['security-scan'])
    expect(result.failedAttestations).toEqual(['unit-tests'])
  })

  it('merges both variants when a flow uses types and names together', () => {
    const result = toAssertResult({
      ...base,
      status: 'NON_COMPLIANT',
      missingAttestationTypes: ['snyk'],
      missingAttestationNames: ['security-scan'],
    })
    expect(result.missingAttestations).toEqual(['snyk', 'security-scan'])
  })

  it('never yields undefined lists, even from a malformed response', () => {
    const result = toAssertResult({} as AssertResponse)
    expect(result.missingAttestations).toEqual([])
    expect(result.failedAttestations).toEqual([])
    expect(result.compliant).toBe(false)
    expect(typeof result.message).toBe('string')
  })

  it('treats a missing status as non-compliant rather than compliant', () => {
    // The old view model read `compliant`, which the server never sends; an
    // absent verdict must never render as a pass.
    expect(toAssertResult({ ...base, status: undefined as never }).compliant).toBe(false)
  })
})
