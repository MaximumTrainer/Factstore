<template>
  <div class="viz-root">
    <div class="flex items-start justify-between gap-4 mb-3">
      <div>
        <h3 class="text-base font-medium text-[#0b0b0b]">Gate activity</h3>
        <p class="text-xs text-[#898781]">Deployment gate evaluations per day</p>
      </div>
      <div v-if="hasActivity" class="flex items-center gap-4">
        <!-- A legend is always present for two series, so identity never rests on colour. -->
        <div data-test="chart-legend" class="flex items-center gap-3 text-xs text-[#52514e]">
          <span class="flex items-center gap-1.5">
            <span class="inline-block w-2.5 h-2.5 rounded-sm" :style="{ background: ALLOWED }" />
            Allowed
          </span>
          <span class="flex items-center gap-1.5">
            <span class="inline-block w-2.5 h-2.5 rounded-sm" :style="{ background: BLOCKED }" />
            Blocked
          </span>
        </div>
        <button
          data-test="toggle-table"
          type="button"
          class="text-xs text-[#2a78d6] hover:underline"
          @click="showTable = !showTable"
        >
          {{ showTable ? 'Chart' : 'Table' }}
        </button>
      </div>
    </div>

    <p v-if="!hasActivity" class="py-6 text-center text-sm text-[#898781]">
      No gate activity in this window.
    </p>

    <template v-else-if="!showTable">
      <!-- Sized to include the x-axis band, so the card never grows an inner scrollbar. -->
      <svg
        :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
        class="w-full h-auto"
        role="img"
        aria-label="Gate evaluations per day, allowed and blocked"
      >
        <!-- Recessive hairline grid: solid, one shade off the surface. -->
        <g>
          <line
            v-for="tick in yTicks"
            :key="`grid-${tick}`"
            :x1="PAD_LEFT"
            :x2="WIDTH - PAD_RIGHT"
            :y1="yFor(tick)"
            :y2="yFor(tick)"
            stroke="#e1e0d9"
            stroke-width="1"
          />
          <text
            v-for="tick in yTicks"
            :key="`ytick-${tick}`"
            :x="PAD_LEFT - 6"
            :y="yFor(tick) + 3"
            text-anchor="end"
            font-size="9"
            fill="#898781"
            style="font-variant-numeric: tabular-nums"
          >
            {{ tick }}
          </text>
        </g>

        <line
          :x1="PAD_LEFT"
          :x2="WIDTH - PAD_RIGHT"
          :y1="yFor(0)"
          :y2="yFor(0)"
          stroke="#c3c2b7"
          stroke-width="1"
        />

        <g v-for="(bucket, i) in perDay" :key="bucket.date" data-test="day-group">
          <!-- Blocked sits on the baseline with rounded data-ends; allowed stacks above,
               separated by a 2px surface gap rather than a border. -->
          <rect
            v-if="bucket.blocked > 0"
            data-test="seg-blocked"
            :x="xFor(i)"
            :y="yFor(bucket.blocked)"
            :width="barWidth"
            :height="heightFor(bucket.blocked)"
            :fill="BLOCKED"
            rx="2"
            :aria-label="`${bucket.date}: ${bucket.blocked} blocked`"
          >
            <title>{{ bucket.date }} — {{ bucket.blocked }} blocked</title>
          </rect>
          <rect
            v-if="bucket.allowed > 0"
            data-test="seg-allowed"
            :x="xFor(i)"
            :y="yFor(bucket.allowed + bucket.blocked)"
            :width="barWidth"
            :height="Math.max(heightFor(bucket.allowed) - (bucket.blocked > 0 ? GAP : 0), 1)"
            :fill="ALLOWED"
            rx="2"
            :aria-label="`${bucket.date}: ${bucket.allowed} allowed`"
          >
            <title>{{ bucket.date }} — {{ bucket.allowed }} allowed</title>
          </rect>
        </g>

        <text
          v-for="bucket in labelledDays"
          :key="`x-${bucket.date}`"
          :x="xFor(bucket.index) + barWidth / 2"
          :y="HEIGHT - 6"
          text-anchor="middle"
          font-size="9"
          fill="#898781"
        >
          {{ shortDate(bucket.date) }}
        </text>
      </svg>
    </template>

    <table v-else data-test="chart-table" class="w-full text-sm">
      <thead>
        <tr class="text-left text-xs text-[#898781]">
          <th class="py-1 font-medium">Day</th>
          <th class="py-1 font-medium text-right">Allowed</th>
          <th class="py-1 font-medium text-right">Blocked</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="bucket in perDay" :key="`row-${bucket.date}`" class="border-t border-[#e1e0d9]">
          <td class="py-1 text-[#52514e]">{{ bucket.date }}</td>
          <td class="py-1 text-right text-[#0b0b0b]" style="font-variant-numeric: tabular-nums">
            {{ bucket.allowed }}
          </td>
          <td class="py-1 text-right text-[#0b0b0b]" style="font-variant-numeric: tabular-nums">
            {{ bucket.blocked }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { GateDayBucket } from '../types'

const props = defineProps<{ perDay: GateDayBucket[] }>()

// Allowed/blocked is a polarity, so the documented warm/cool pair is used rather than
// green/red: green vs red measures ΔE 4.1 under deuteranopia and is unreadable for a
// large minority of readers. Blue↔red clears every check, including all-pairs.
const ALLOWED = '#2a78d6'
const BLOCKED = '#d03b3b'

const WIDTH = 720
const HEIGHT = 200
const PAD_LEFT = 28
const PAD_RIGHT = 8
const PAD_TOP = 8
const PAD_BOTTOM = 22
const GAP = 2
const MAX_BAR_WIDTH = 26
const BAR_SPACING = 4

const showTable = ref(false)

const hasActivity = computed(() =>
  props.perDay.some(bucket => bucket.allowed > 0 || bucket.blocked > 0)
)

const maxTotal = computed(() =>
  Math.max(1, ...props.perDay.map(bucket => bucket.allowed + bucket.blocked))
)

const plotHeight = HEIGHT - PAD_TOP - PAD_BOTTOM

const plotWidth = WIDTH - PAD_LEFT - PAD_RIGHT

const slotWidth = computed(() => plotWidth / Math.max(props.perDay.length, 1))

/** Capped so a short window draws thin marks rather than a wall of saturated colour. */
const barWidth = computed(() => Math.min(Math.max(slotWidth.value - 3, 2), MAX_BAR_WIDTH))

/** Left-align inside the plot, but centre the group when the bars are narrower than their slots. */
const groupOffset = computed(() => {
  const used = props.perDay.length * (barWidth.value + BAR_SPACING)
  return Math.max((plotWidth - used) / 2, 0)
})

function xFor(index: number): number {
  if (groupOffset.value > 0) {
    return PAD_LEFT + groupOffset.value + index * (barWidth.value + BAR_SPACING)
  }
  return PAD_LEFT + index * slotWidth.value + (slotWidth.value - barWidth.value) / 2
}

function yFor(value: number): number {
  return PAD_TOP + plotHeight - (value / maxTotal.value) * plotHeight
}

function heightFor(value: number): number {
  return Math.max((value / maxTotal.value) * plotHeight, 2)
}

const yTicks = computed(() => {
  const max = maxTotal.value
  const step = Math.max(1, Math.ceil(max / 3))
  const ticks: number[] = []
  for (let value = 0; value <= max; value += step) ticks.push(value)
  return ticks
})

/** At most eight x labels, so they never collide on a 90-day window. */
const labelledDays = computed(() => {
  const stride = Math.max(1, Math.ceil(props.perDay.length / 8))
  return props.perDay
    .map((bucket, index) => ({ ...bucket, index }))
    .filter(bucket => bucket.index % stride === 0)
})

function shortDate(date: string): string {
  return date.slice(5)
}
</script>
