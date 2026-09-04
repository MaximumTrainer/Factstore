<template>
  <div class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50 p-4">
    <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-lg">
      <h2 class="text-lg font-bold text-gray-900 mb-2">Remove this trail?</h2>
      <p class="text-sm text-gray-600 mb-4">
        Trail <span class="font-mono">{{ trail.id }}</span> —
        commit <span class="font-mono">{{ trail.gitCommitSha.slice(0, 8) }}</span> on
        {{ trail.gitBranch }}.
      </p>

      <!-- State exactly what would be destroyed, itemised, before asking. -->
      <div
        data-test="cascade-summary"
        class="mb-4 p-3 rounded-md bg-red-50 border border-red-200 text-sm text-red-800"
      >
        <p class="font-medium mb-1">Deleting is permanent and would also remove:</p>
        <ul v-if="cascade" class="list-disc list-inside space-y-0.5">
          <li v-for="item in itemised" :key="item.label">{{ item.count }} {{ item.label }}</li>
          <li v-if="!itemised.length">no other records</li>
        </ul>
        <p v-else class="text-red-700">Counting what this trail owns…</p>
      </div>

      <p class="mb-4 text-xs text-gray-500">
        The audit log and the append-only ledger are left intact, so the record that this evidence
        existed survives its removal.
      </p>

      <div class="mb-4 p-3 rounded-md bg-gray-50 border border-gray-200">
        <p class="text-sm text-gray-700 mb-2">
          <strong>Archiving</strong> hides the trail from the listings and keeps every piece of
          evidence. It is reversible, and is almost always what you want.
        </p>
        <button
          data-test="archive-instead"
          type="button"
          class="px-3 py-1.5 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
          :disabled="busy"
          @click="archive"
        >
          Archive instead
        </button>
      </div>

      <label class="block text-sm font-medium text-gray-700 mb-1">
        To delete permanently, type the trail id
      </label>
      <input
        v-model="confirmation"
        data-test="confirm-input"
        type="text"
        :placeholder="trail.id"
        autocomplete="off"
        class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-red-500"
      />

      <p v-if="error" data-test="delete-error" class="mt-3 text-sm text-red-600">{{ error }}</p>

      <div class="mt-6 flex justify-end gap-3">
        <button
          data-test="cancel-delete"
          type="button"
          class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
          @click="emit('cancel')"
        >
          Cancel
        </button>
        <button
          data-test="confirm-delete"
          type="button"
          :disabled="!confirmed || busy"
          class="px-4 py-2 text-sm text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-50"
          @click="remove"
        >
          {{ busy ? 'Working…' : 'Delete permanently' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { archiveTrail, deleteTrail, getTrailCascade } from '../api/trails'
import type { Trail, TrailCascadeCounts } from '../types'

const props = defineProps<{ trail: Trail }>()
const emit = defineEmits<{ deleted: [id: string]; archived: [trail: Trail]; cancel: [] }>()

const cascade = ref<TrailCascadeCounts | null>(null)
const confirmation = ref('')
const busy = ref(false)
const error = ref('')

const LABELS: Array<[keyof TrailCascadeCounts, string]> = [
  ['attestations', 'attestations'],
  ['artifacts', 'artifacts'],
  ['evidenceFiles', 'evidence files'],
  ['approvals', 'approvals'],
  ['coverageReports', 'coverage reports'],
  ['securityScans', 'security scans'],
  ['complianceAssessments', 'compliance assessments'],
  ['jiraTickets', 'Jira tickets'],
]

const itemised = computed(() =>
  cascade.value
    ? LABELS.filter(([key]) => (cascade.value![key] as number) > 0).map(([key, label]) => ({
        label,
        count: cascade.value![key] as number,
      }))
    : []
)

// A typed confirmation, not just a second click: this destroys compliance evidence.
const confirmed = computed(() => confirmation.value.trim() === props.trail.id)

async function remove() {
  if (!confirmed.value) return
  busy.value = true
  error.value = ''
  try {
    await deleteTrail(props.trail.id)
    emit('deleted', props.trail.id)
  } catch {
    error.value = 'Failed to delete the trail. Nothing was removed.'
  } finally {
    busy.value = false
  }
}

async function archive() {
  busy.value = true
  error.value = ''
  try {
    const { data } = await archiveTrail(props.trail.id)
    emit('archived', data)
  } catch {
    error.value = 'Failed to archive the trail.'
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await getTrailCascade(props.trail.id)
    cascade.value = data
  } catch {
    // Without the counts the dialog still works; it just cannot itemise.
  }
})
</script>
