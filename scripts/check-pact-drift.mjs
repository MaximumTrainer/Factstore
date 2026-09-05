#!/usr/bin/env node
/**
 * Fails if a generated pact file no longer matches the one committed to the repository.
 *
 * The committed contracts had silently gone stale: a `tags` field was added to the flow
 * contract and the files under `pacts/` never caught up. Nothing noticed, because the two
 * places that read them disagree about which copy is authoritative --
 *
 *   - CI regenerates the pacts in the consumer jobs, uploads them as artifacts, and the
 *     provider job verifies against *those*, overwriting whatever is committed;
 *   - `PactProviderVerificationTest` is annotated `@PactFolder("../pacts")`, so a developer
 *     running `./gradlew contractTest` locally verifies against the *committed* copy.
 *
 * So local verification was checking a weaker contract than CI, and a checked-in contract that
 * nobody verifies is not a contract. This makes the drift visible where it happens.
 *
 * `metadata` is ignored on purpose: it records pact tooling versions, which change on every
 * dependency bump and say nothing about the agreement between consumer and provider. A check
 * that cried wolf on every Dependabot PR would be switched off within a week.
 */
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const file = process.argv[2]
if (!file) {
  console.error('usage: check-pact-drift.mjs <path-to-pact-file>')
  process.exit(2)
}

const contractOf = raw => {
  const { metadata, ...contract } = JSON.parse(raw)
  return contract
}

let committedRaw
try {
  committedRaw = execFileSync('git', ['show', `HEAD:${file}`], { encoding: 'utf8' })
} catch {
  console.error(`${file} is not committed. Add it, so the contract is reviewable.`)
  process.exit(1)
}

const generated = contractOf(readFileSync(file, 'utf8'))
const committed = contractOf(committedRaw)

const asText = value => JSON.stringify(value, null, 2)

if (asText(generated) === asText(committed)) {
  console.log(`${file} matches the committed contract.`)
  process.exit(0)
}

console.error(`${file} does not match the committed contract.`)
console.error('')
console.error('The consumer tests generated a different contract from the one in the repo.')
console.error('Commit the regenerated file so the checked-in contract, and the provider')
console.error('verification that runs against it locally, match what the consumer expects:')
console.error('')
console.error(`    npm run test:pact && git add ${file}`)
console.error('')

const gi = generated.interactions ?? []
const ci = committed.interactions ?? []
const describe = i => `${i.request?.method} ${i.request?.path} -> ${i.response?.status}`
const only = (a, b) => a.filter(x => !b.some(y => describe(y) === describe(x))).map(describe)

const added = only(gi, ci)
const removed = only(ci, gi)
if (added.length) console.error(`  interactions only in the generated file: ${added.join(', ')}`)
if (removed.length) console.error(`  interactions only in the committed file: ${removed.join(', ')}`)
if (!added.length && !removed.length) {
  console.error('  the same interactions, but their request or response bodies differ.')
}

process.exit(1)
