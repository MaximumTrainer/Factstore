<template>
  <div>
    <div class="flex flex-wrap items-start justify-between gap-4 mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Delivery Metrics</h1>
        <p class="mt-1 text-sm text-gray-500">
          DORA measures where Factstore's own records support them, and gate activity throughout.
        </p>
      </div>

      <!-- One filter row above everything it scopes, so every panel shows the same slice. -->
      <div class="flex items-center gap-1 bg-white rounded-md shadow p-1" role="group" aria-label="Time window">
        <button
          v-for="option in WINDOWS"
          :key="option"
          data-test="window-option"
          type="button"
          :aria-pressed="windowDays === option"
          :class="windowDays === option ? 'bg-indigo-600 text-white' : 'text-gray-600 hover:bg-gray-50'"
          class="px-3 py-1.5 rounded text-sm font-medium"
          @click="select(option)"
        >
          {{ option }} days
        </button>
      </div>
    </div>

    <p v-if="loadError" class="mb-6 text-sm text-red-600">{{ loadError }}</p>

    <!-- Hold the previous render at reduced opacity on refetch, rather than flashing a
         skeleton and jumping the layout. -->
    <div v-if="metrics" :class="{ 'opacity-60': loading }" class="transition-opacity">
      <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <StatTile label="Deployment frequency" :metric="metrics.deploymentFrequency" />
        <StatTile label="Lead time for changes" :metric="metrics.leadTimeForChanges" />
        <StatTile label="Change failure rate" :metric="metrics.changeFailureRate" />
        <StatTile label="Time to restore service" :metric="metrics.timeToRestoreService" />
      </div>

      <div class="bg-white rounded-lg shadow p-5 mb-6">
        <GateActivityChart :per-day="metrics.gates.perDay" />
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-5">
          <RankedBars
            title="Why gates blocked"
            subtitle="Most common reason a deployment was stopped"
            :items="metrics.gates.topBlockReasons"
            empty-message="No gate blocked a deployment in this window."
          />
        </div>
        <div class="bg-white rounded-lg shadow p-5">
          <RankedBars
            title="Gates holding releases back"
            subtitle="Attestations most often missing when an assertion failed"
            :items="metrics.assertions.topMissingAttestations"
            empty-message="No assertion was blocked by a missing attestation in this window."
          />
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="bg-white rounded-lg shadow p-5">
          <h3 class="text-base font-medium text-gray-900 mb-3">Deployment gates</h3>
          <dl class="text-sm space-y-1.5">
            <div class="flex justify-between">
              <dt class="text-gray-500">Evaluations</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.gates.evaluations }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-gray-500">Allowed</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.gates.allowed }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-gray-500">Blocked</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.gates.blocked }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-gray-500">Block rate</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.gates.blockRate }}%</dd>
            </div>
          </dl>
        </div>
        <div class="bg-white rounded-lg shadow p-5">
          <h3 class="text-base font-medium text-gray-900 mb-3">Compliance assertions</h3>
          <dl class="text-sm space-y-1.5">
            <div class="flex justify-between">
              <dt class="text-gray-500">Evaluations</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.assertions.evaluations }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-gray-500">Compliant</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.assertions.compliant }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-gray-500">Blocked</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.assertions.blocked }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-gray-500">Block rate</dt>
              <dd class="text-gray-900 tabular-nums">{{ metrics.assertions.blockRate }}%</dd>
            </div>
          </dl>
        </div>
      </div>

      <p class="mt-6 text-xs text-gray-400">
        Window: {{ formatDate(metrics.from) }} — {{ formatDate(metrics.to) }}
        ({{ metrics.windowDays }} days).
      </p>
    </div>

    <div v-else-if="loading" class="text-center text-gray-500 py-12">Loading metrics…</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GateActivityChart from '../components/GateActivityChart.vue'
import RankedBars from '../components/RankedBars.vue'
import StatTile from '../components/StatTile.vue'
import { getDeliveryMetrics } from '../api/dashboard'
import type { DeliveryMetrics } from '../types'

const WINDOWS = [7, 30, 90]

const metrics = ref<DeliveryMetrics | null>(null)
const windowDays = ref(30)
const loading = ref(true)
const loadError = ref('')

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await getDeliveryMetrics(windowDays.value)
    metrics.value = data
  } catch {
    loadError.value = 'Failed to load delivery metrics.'
  } finally {
    loading.value = false
  }
}

function select(days: number) {
  windowDays.value = days
  load()
}

onMounted(load)
</script>
