<template>
  <div>
    <h3 class="text-base font-medium text-[#0b0b0b]">{{ title }}</h3>
    <p class="text-xs text-[#898781] mb-3">{{ subtitle }}</p>

    <p v-if="!items.length" class="py-6 text-center text-sm text-[#898781]">{{ emptyMessage }}</p>

    <!-- One series, so one colour for every bar: shading by length would burn the only
         free channel on information the bar already carries. Every bar is directly
         labelled, so the value never depends on a hover. -->
    <ul v-else class="space-y-2">
      <li v-for="item in items" :key="item.value" data-test="ranked-row">
        <div class="flex items-baseline justify-between gap-3 mb-1">
          <span class="text-sm text-[#0b0b0b] truncate" :title="item.value">{{ item.value }}</span>
          <span
            class="text-sm text-[#52514e] shrink-0"
            style="font-variant-numeric: tabular-nums"
            >{{ item.count }}</span
          >
        </div>
        <div class="h-1.5 rounded-sm bg-[#e1e0d9]">
          <div
            data-test="ranked-bar"
            class="h-1.5 rounded-sm"
            :style="{ width: `${widthFor(item.count)}%`, background: SERIES_1 }"
          />
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CountedValue } from '../types'

const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    items: CountedValue[]
    emptyMessage?: string
  }>(),
  { subtitle: '', emptyMessage: 'Nothing to report in this window.' }
)

const SERIES_1 = '#2a78d6'

const max = computed(() => Math.max(1, ...props.items.map(item => item.count)))

function widthFor(count: number): number {
  return Math.max((count / max.value) * 100, 2)
}
</script>
