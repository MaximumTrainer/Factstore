<template>
  <div>
    <div class="mb-6">
      <RouterLink :to="`/environments/${envId}`" class="text-indigo-600 hover:text-indigo-900 text-sm font-medium">
        ← Back to Environment
      </RouterLink>
    </div>

    <h1 class="text-2xl font-bold text-gray-900 mb-6">Compare Snapshots</h1>

    <!-- Selector -->
    <div class="bg-white shadow rounded-lg p-6 mb-6">
      <div class="flex flex-wrap items-end gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Snapshot A (older)</label>
          <select
            v-model.number="indexA"
            class="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option v-for="s in snapshots" :key="s.snapshotIndex" :value="s.snapshotIndex">
              #{{ s.snapshotIndex }} — {{ new Date(s.recordedAt).toLocaleString() }}
            </option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Snapshot B (newer)</label>
          <select
            v-model.number="indexB"
            class="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option v-for="s in snapshots" :key="s.snapshotIndex" :value="s.snapshotIndex">
              #{{ s.snapshotIndex }} — {{ new Date(s.recordedAt).toLocaleString() }}
            </option>
          </select>
        </div>
        <button
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
          :disabled="comparing || snapshots.length < 2"
          @click="compare"
        >
          {{ comparing ? 'Comparing…' : 'Compare' }}
        </button>
      </div>
      <p v-if="compareError" class="mt-3 text-sm text-red-600">{{ compareError }}</p>
    </div>

    <!-- Diff table -->
    <div v-if="diffRows.length > 0" class="bg-white shadow rounded-lg">
      <div class="px-6 py-4 border-b border-gray-200 flex items-center gap-4">
        <h2 class="text-lg font-semibold text-gray-900">Diff: Snapshot #{{ shownA }} → #{{ shownB }}</h2>
        <div class="flex gap-3 text-xs">
          <span class="bg-green-100 text-green-800 px-2 py-0.5 rounded">✨ {{ addedCount }} Added</span>
          <span class="bg-red-100 text-red-800 px-2 py-0.5 rounded">🗑️ {{ removedCount }} Removed</span>
          <span class="bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded">🔄 {{ changedCount }} Changed</span>
          <span class="bg-gray-100 text-gray-700 px-2 py-0.5 rounded">✅ {{ unchangedCount }} Unchanged</span>
        </div>
      </div>
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Image Name</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Tag</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">SHA256</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr
            v-for="(row, idx) in diffRows"
            :key="idx"
            :class="rowClass(row.status)"
          >
            <td class="px-6 py-3 text-sm font-medium text-gray-900">{{ row.name }}</td>
            <td class="px-6 py-3 text-sm text-gray-600 font-mono">{{ row.tag }}</td>
            <td class="px-6 py-3 text-sm text-gray-500 font-mono text-xs">{{ row.sha.slice(0, 20) }}…</td>
            <td class="px-6 py-3 text-sm font-medium">{{ statusLabel(row.status) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="!comparing && snapshots.length === 0" class="text-center text-gray-500 py-12">
      No snapshots available to compare.
    </div>

    <div v-if="loadingSnapshots" class="text-center text-gray-500 py-12">Loading snapshots…</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listSnapshots, getSnapshot } from '../api/environments'
import type { EnvironmentSnapshot, SnapshotArtifact } from '../types'

type DiffStatus = 'added' | 'removed' | 'changed' | 'unchanged'

interface DiffRow {
  name: string
  tag: string
  sha: string
  status: DiffStatus
}

const route = useRoute()
const envId = route.params.id as string

const snapshots = ref<EnvironmentSnapshot[]>([])
const loadingSnapshots = ref(true)
const loadError = ref('')

const indexA = ref<number>(0)
const indexB = ref<number>(0)

const comparing = ref(false)
const compareError = ref('')

const diffRows = ref<DiffRow[]>([])
const shownA = ref<number | null>(null)
const shownB = ref<number | null>(null)

const addedCount = computed(() => diffRows.value.filter(r => r.status === 'added').length)
const removedCount = computed(() => diffRows.value.filter(r => r.status === 'removed').length)
const changedCount = computed(() => diffRows.value.filter(r => r.status === 'changed').length)
const unchangedCount = computed(() => diffRows.value.filter(r => r.status === 'unchanged').length)

function rowClass(status: DiffStatus): string {
  switch (status) {
    case 'added': return 'bg-green-50'
    case 'removed': return 'bg-red-50'
    case 'changed': return 'bg-yellow-50'
    default: return ''
  }
}

function statusLabel(status: DiffStatus): string {
  switch (status) {
    case 'added': return '✨ Added'
    case 'removed': return '🗑️ Removed'
    case 'changed': return '🔄 Changed'
    default: return '✅ Unchanged'
  }
}

function computeDiff(a: SnapshotArtifact[], b: SnapshotArtifact[]): DiffRow[] {
  const rows: DiffRow[] = []

  const aMap = new Map(a.map(art => [art.artifactName, art]))
  const bMap = new Map(b.map(art => [art.artifactName, art]))

  const allNames = new Set([...aMap.keys(), ...bMap.keys()])

  for (const name of allNames) {
    const aArt = aMap.get(name)
    const bArt = bMap.get(name)

    if (aArt && !bArt) {
      rows.push({ name, tag: aArt.artifactTag, sha: aArt.artifactSha256, status: 'removed' })
    } else if (!aArt && bArt) {
      rows.push({ name, tag: bArt.artifactTag, sha: bArt.artifactSha256, status: 'added' })
    } else if (aArt && bArt) {
      const changed = aArt.artifactSha256 !== bArt.artifactSha256 || aArt.artifactTag !== bArt.artifactTag
      rows.push({ name, tag: bArt.artifactTag, sha: bArt.artifactSha256, status: changed ? 'changed' : 'unchanged' })
    }
  }

  const order: DiffStatus[] = ['removed', 'added', 'changed', 'unchanged']
  rows.sort((r1, r2) => order.indexOf(r1.status) - order.indexOf(r2.status))
  return rows
}

async function compare() {
  if (indexA.value === indexB.value) {
    compareError.value = 'Please select two different snapshots.'
    return
  }
  comparing.value = true
  compareError.value = ''
  diffRows.value = []
  try {
    const [resA, resB] = await Promise.all([
      getSnapshot(envId, indexA.value),
      getSnapshot(envId, indexB.value)
    ])
    shownA.value = indexA.value
    shownB.value = indexB.value
    diffRows.value = computeDiff(resA.data.artifacts, resB.data.artifacts)
  } catch (err) {
    console.error('Failed to compare snapshots', err)
    compareError.value = 'Failed to load snapshots for comparison.'
  } finally {
    comparing.value = false
  }
}

onMounted(async () => {
  try {
    const res = await listSnapshots(envId)
    snapshots.value = res.data
    if (res.data.length >= 2) {
      indexA.value = res.data[0].snapshotIndex
      indexB.value = res.data[res.data.length - 1].snapshotIndex
    } else if (res.data.length === 1) {
      indexA.value = res.data[0].snapshotIndex
      indexB.value = res.data[0].snapshotIndex
    }
  } catch (err) {
    console.error('Failed to load snapshots', err)
    loadError.value = 'Failed to load snapshots. Please try again.'
  } finally {
    loadingSnapshots.value = false
  }
})
</script>
