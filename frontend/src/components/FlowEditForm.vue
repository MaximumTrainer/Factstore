<template>
  <form @submit.prevent="save">
    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
      <input
        v-model="form.name"
        data-test="flow-name"
        type="text"
        required
        class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
    </div>

    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
      <textarea
        v-model="form.description"
        data-test="flow-description"
        rows="2"
        class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
      ></textarea>
    </div>

    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Required attestation types</label>
      <input
        v-model="form.attestationTypes"
        data-test="flow-attestation-types"
        type="text"
        placeholder="junit, snyk, ghas"
        class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
      <p class="mt-1 text-xs text-gray-500">Comma-separated.</p>
      <div v-if="suggestions.length" class="mt-2 flex flex-wrap items-center gap-2">
        <span class="text-xs text-gray-500">Add:</span>
        <button
          v-for="type in suggestions"
          :key="type"
          data-test="attestation-type-suggestion"
          type="button"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-700 hover:bg-indigo-100 hover:text-indigo-800"
          @click="addType(type)"
        >
          + {{ type }}
        </button>
      </div>
    </div>

    <!-- Changing the required gates changes how existing trails evaluate on their next
         assert, so state the blast radius before the user commits to it. -->
    <div
      v-if="showImpactWarning"
      data-test="flow-impact-warning"
      class="mb-4 p-3 rounded-md bg-amber-50 border border-amber-200 text-sm text-amber-800"
    >
      <strong>{{ impact?.trailCount }}</strong>
      {{ impact?.trailCount === 1 ? 'trail is' : 'trails are' }} attached to this flow and will be
      judged against the new requirements on their next assertion<span v-if="impact?.pendingTrailCount">,
      including {{ impact.pendingTrailCount }} still pending</span>.
    </div>

    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Tags</label>
      <input
        v-model="form.tags"
        data-test="flow-tags"
        type="text"
        placeholder="team=payments, tier=1"
        class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
      <p class="mt-1 text-xs text-gray-500">Comma-separated <code>key=value</code> pairs.</p>
    </div>

    <div class="mb-4">
      <label class="flex items-center gap-2 text-sm font-medium text-gray-700">
        <input
          v-model="form.requiresApproval"
          data-test="flow-requires-approval"
          type="checkbox"
          class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
        />
        Requires approval before a release is compliant
      </label>
    </div>

    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Approver roles</label>
      <input
        v-model="form.approverRoles"
        data-test="flow-approver-roles"
        type="text"
        placeholder="release-manager, security"
        class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
    </div>

    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Flow template (YAML)</label>
      <textarea
        v-model="form.templateYaml"
        data-test="flow-template-yaml"
        rows="8"
        spellcheck="false"
        placeholder="version: 1&#10;artifacts: []"
        class="w-full border border-gray-300 rounded-md px-3 py-2 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
      ></textarea>
      <p class="mt-1 text-xs text-gray-500">
        A template requires attestations by <em>name</em> and takes precedence over the types above.
      </p>
    </div>

    <p v-if="error" data-test="flow-error" class="mb-4 text-sm text-red-600">{{ error }}</p>

    <div class="flex justify-end gap-3">
      <button
        data-test="flow-cancel"
        type="button"
        class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
        @click="emit('cancel')"
      >
        Cancel
      </button>
      <button
        data-test="flow-save"
        type="submit"
        :disabled="saving"
        class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
      >
        {{ saving ? 'Saving…' : 'Save changes' }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { updateFlow, getFlowImpact, type UpdateFlowPayload } from '../api/flows'
import { listAttestationTypes } from '../api/attestationTypes'
import type { Flow, FlowImpact } from '../types'

const props = defineProps<{ flow: Flow }>()
const emit = defineEmits<{ saved: [flow: Flow]; cancel: [] }>()

const form = ref({
  name: props.flow.name,
  description: props.flow.description ?? '',
  attestationTypes: (props.flow.requiredAttestationTypes ?? []).join(', '),
  tags: Object.entries(props.flow.tags ?? {})
    .map(([key, value]) => `${key}=${value}`)
    .join(', '),
  requiresApproval: props.flow.requiresApproval ?? false,
  approverRoles: (props.flow.requiredApproverRoles ?? []).join(', '),
  templateYaml: props.flow.templateYaml ?? '',
})

const saving = ref(false)
const error = ref('')
const impact = ref<FlowImpact | null>(null)
const knownTypes = ref<string[]>([])

function splitList(raw: string): string[] {
  return raw
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
}

function parseTags(raw: string): Record<string, string> {
  const tags: Record<string, string> = {}
  raw.split(',').forEach(pair => {
    const idx = pair.indexOf('=')
    if (idx > 0) {
      const key = pair.slice(0, idx).trim()
      if (key) tags[key] = pair.slice(idx + 1).trim()
    }
  })
  return tags
}

const requiredTypes = computed(() => splitList(form.value.attestationTypes))

const suggestions = computed(() =>
  knownTypes.value.filter(type => !requiredTypes.value.includes(type))
)

const gatesChanged = computed(() => {
  const original = props.flow.requiredAttestationTypes ?? []
  const current = requiredTypes.value
  return original.length !== current.length || original.some((type, i) => type !== current[i])
})

const showImpactWarning = computed(
  () => gatesChanged.value && (impact.value?.trailCount ?? 0) > 0
)

function addType(type: string) {
  form.value.attestationTypes = [...requiredTypes.value, type].join(', ')
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    const payload: UpdateFlowPayload = {
      name: form.value.name,
      description: form.value.description,
      requiredAttestationTypes: requiredTypes.value,
      tags: parseTags(form.value.tags),
      requiresApproval: form.value.requiresApproval,
      requiredApproverRoles: splitList(form.value.approverRoles),
    }
    // Sending an empty string would replace a template rather than leave it alone; the
    // server treats an absent field as "unchanged".
    if (form.value.templateYaml.trim()) {
      payload.templateYaml = form.value.templateYaml
    }
    const { data } = await updateFlow(props.flow.id, payload)
    emit('saved', data)
  } catch {
    error.value = 'Failed to save the flow. The name may already be in use.'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await getFlowImpact(props.flow.id)
    impact.value = data
  } catch {
    // Without the impact we simply do not show the warning.
  }
  try {
    const { data } = await listAttestationTypes()
    knownTypes.value = data.map(type => type.name)
  } catch {
    // Suggestions are a convenience; the field is free text.
  }
})
</script>
