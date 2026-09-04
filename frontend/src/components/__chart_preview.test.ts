import { describe, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { writeFileSync } from 'node:fs'
import GateActivityChart from './GateActivityChart.vue'
import RankedBars from './RankedBars.vue'
import StatTile from './StatTile.vue'
import type { GateDayBucket } from '../types'

/**
 * Not an assertion test: dumps the real rendered markup of the chart components to an HTML
 * file so a human (or a screenshot) can check geometry, label collisions and overflow —
 * the things a palette validator cannot see.
 *
 * Only runs when CHART_PREVIEW_OUT is set, so it is inert in CI.
 */
const OUT = process.env.CHART_PREVIEW_OUT

const thirtyDays = (): GateDayBucket[] =>
  Array.from({ length: 30 }, (_, i) => {
    const date = new Date(Date.UTC(2026, 7, 6 + i)).toISOString().slice(0, 10)
    // A realistic-looking week rhythm: quiet weekends, occasional block spikes.
    const weekday = new Date(date).getUTCDay()
    const busy = weekday !== 0 && weekday !== 6
    return {
      date,
      allowed: busy ? 3 + ((i * 7) % 9) : 0,
      blocked: busy && i % 4 === 0 ? 1 + (i % 3) : 0,
    }
  })

describe('chart preview', () => {
  it.runIf(OUT)('writes a preview page', () => {
    const chart = mount(GateActivityChart, { props: { perDay: thirtyDays() } })
    const sparse = mount(GateActivityChart, {
      props: {
        perDay: [
          { date: '2026-09-01', allowed: 1, blocked: 0 },
          { date: '2026-09-02', allowed: 0, blocked: 0 },
          { date: '2026-09-03', allowed: 0, blocked: 12 },
        ],
      },
    })
    const empty = mount(GateActivityChart, {
      props: { perDay: [{ date: '2026-09-01', allowed: 0, blocked: 0 }] },
    })
    const ranked = mount(RankedBars, {
      props: {
        title: 'Why gates blocked',
        subtitle: 'Most common reason a deployment was stopped',
        items: [
          { value: 'missing attestation: container-image-scan', count: 24 },
          { value: 'missing attestation: snyk', count: 17 },
          { value: 'approval required but not yet granted', count: 9 },
          { value: 'critical vulnerabilities above threshold', count: 4 },
          { value: 'signature not verified', count: 1 },
        ],
      },
    })
    const tiles = [
      { label: 'Deployment frequency', value: 2.4, unit: 'per day', sampleSize: 72, available: true, basis: 'Deployments recorded to any environment, divided by the days in the window.' },
      { label: 'Lead time for changes', value: 13.333, unit: 'hours', sampleSize: 68, available: true, basis: "Median time from the creation of the trail that produced an artifact to that artifact's deployment." },
      { label: 'Change failure rate', value: 12.5, unit: 'percent', sampleSize: 96, available: true, basis: 'Share of deployment gate evaluations that blocked the release. A pre-deployment gate rate, not DORA’s post-release change failure rate.' },
      { label: 'Time to restore service', value: null, unit: 'hours', sampleSize: 0, available: false, basis: 'Not derivable from Factstore data: no incident or outage records are kept here.' },
    ].map(({ label, ...metric }) => mount(StatTile, { props: { label, metric } }).html())

    writeFileSync(
      OUT!,
      `<!doctype html><html><head><meta charset="utf-8">
<script src="https://cdn.tailwindcss.com"></script>
<style>body{background:#f9f9f7;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;margin:0;padding:24px}
h2{font-size:12px;text-transform:uppercase;letter-spacing:.04em;color:#898781;margin:24px 0 8px}
.card{background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);padding:20px}</style>
</head><body>
<h2>Stat tiles</h2>
<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px">${tiles.join('')}</div>
<h2>Gate activity — 30 days</h2><div class="card">${chart.html()}</div>
<h2>Gate activity — sparse, one tall block</h2><div class="card">${sparse.html()}</div>
<h2>Gate activity — no activity</h2><div class="card">${empty.html()}</div>
<h2>Ranked bars — long labels</h2><div class="card" style="max-width:520px">${ranked.html()}</div>
</body></html>`,
      'utf-8'
    )
  })
})
