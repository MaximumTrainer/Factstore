import client from './client'
import type { AssertResponse, AssertResult } from '../types'

/**
 * Maps the server's AssertResponse onto the view model the assert screens render.
 *
 * The server reports the verdict as `status`, and populates *either* the type-based lists
 * (flows defined with requiredAttestationTypes) *or* the name-based lists (template-driven
 * flows). Everything is defaulted here so a malformed or partial response can never put an
 * `undefined` in front of a template.
 */
export function toAssertResult(response: AssertResponse): AssertResult {
  const compliant = response?.status === 'COMPLIANT'
  return {
    compliant,
    status: compliant ? 'COMPLIANT' : 'NON_COMPLIANT',
    sha256Digest: response?.sha256Digest ?? '',
    flowId: response?.flowId ?? '',
    trailId: response?.trailId,
    message: response?.details ?? '',
    missingAttestations: [
      ...(response?.missingAttestationTypes ?? []),
      ...(response?.missingAttestationNames ?? []),
    ],
    failedAttestations: [
      ...(response?.failedAttestationTypes ?? []),
      ...(response?.failedAttestationNames ?? []),
    ],
  }
}

/**
 * Asserts an artifact against a flow. Pass `trailId` to scope the verdict to one pipeline
 * execution; without it the most recent trail carrying the digest decides.
 */
export const assertCompliance = async (
  sha256Digest: string,
  flowId: string,
  trailId?: string
): Promise<AssertResult> => {
  const { data } = await client.post<AssertResponse>('/assert', { sha256Digest, flowId, trailId })
  return toAssertResult(data)
}

/** Asserts a specific pipeline execution; needs no digest. */
export const assertTrail = async (
  trailId: string,
  options: { flowId?: string; sha256Digest?: string } = {}
): Promise<AssertResult> => {
  const { data } = await client.post<AssertResponse>(`/trails/${trailId}/assert`, options)
  return toAssertResult(data)
}

export const getChainOfCustody = (sha256: string) =>
  client.get(`/compliance/artifact/${sha256}`)
