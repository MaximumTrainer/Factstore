<template>
  <div class="bg-white rounded-lg shadow p-5 flex flex-col">
    <p class="text-sm text-[#52514e]">{{ label }}</p>

    <p class="mt-2 flex items-baseline gap-1.5">
      <!-- Proportional figures: tabular-nums makes a display-size number look loose. -->
      <span
        data-test="stat-value"
        class="text-3xl font-semibold"
        :class="metric.available ? 'text-[#0b0b0b]' : 'text-[#898781]'"
        >{{ formattedValue }}</span
      >
      <span v-if="metric.available && unitSuffix" class="text-sm text-[#52514e]">{{ unitSuffix }}</span>
    </p>

    <p v-if="!metric.available" class="mt-1 text-xs font-medium text-[#898781]">Not available</p>
    <p v-else data-test="stat-sample" class="mt-1 text-xs text-[#898781]">
      from {{ metric.sampleSize.toLocaleString() }}
      {{ metric.sampleSize === 1 ? 'data point' : 'data points' }}
    </p>

    <!-- What the number actually measures, always on the tile. A metric whose basis is
         hidden gets read as whatever the reader assumes it means. -->
    <p data-test="stat-basis" class="mt-3 text-xs leading-snug text-[#898781]">
      {{ metric.basis }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DoraMetric } from '../types'

const props = defineProps<{ label: string; metric: DoraMetric }>()

/** Percent carries its sign in the value itself; other units sit beside it. */
const unitSuffix = computed(() => (props.metric.unit === 'percent' ? '' : props.metric.unit))

const formattedValue = computed(() => {
  const { value, available, unit } = props.metric
  if (!available || value == null) return '—'
  if (unit === 'percent') return `${round(value)}%`
  return compact(value)
})

function round(value: number): string {
  return String(Math.round(value * 10) / 10)
}

function compact(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${round(value / 1_000_000)}M`
  if (Math.abs(value) >= 1_000) return `${round(value / 1_000)}K`
  return round(value)
}
</script>
