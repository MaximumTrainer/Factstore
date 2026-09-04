<template>
  <div>
    <p class="text-sm font-medium text-gray-700 mb-1">Start from a template</p>
    <p class="text-xs text-gray-500 mb-3">
      A template pre-populates the gates your organisation expects for this kind of service. You
      can edit them before saving.
    </p>

    <div class="flex flex-wrap gap-2 mb-3">
      <button
        data-test="blank-flow"
        type="button"
        :class="selectedServiceType === null && selectedFrameworks.length === 0
          ? 'bg-indigo-600 text-white'
          : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'"
        class="px-3 py-1.5 rounded-md text-sm font-medium"
        @click="clear"
      >
        Blank flow
      </button>
      <button
        v-for="template in serviceTypeTemplates"
        :key="template.id"
        data-test="service-type-option"
        type="button"
        :title="template.description"
        :class="selectedServiceType === template.id
          ? 'bg-indigo-600 text-white'
          : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'"
        class="px-3 py-1.5 rounded-md text-sm font-medium"
        @click="chooseServiceType(template.id)"
      >
        {{ template.name }}
      </button>
    </div>

    <template v-if="frameworkTemplates.length">
      <p class="text-xs text-gray-500 mb-2">
        Add a regulatory framework — its requirements are combined with the service type's.
      </p>
      <div class="flex flex-wrap gap-2 mb-3">
        <button
          v-for="template in frameworkTemplates"
          :key="template.id"
          data-test="framework-option"
          type="button"
          :title="template.description"
          :class="selectedFrameworks.includes(template.id)
            ? 'bg-purple-600 text-white'
            : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'"
          class="px-3 py-1.5 rounded-md text-sm font-medium"
          @click="toggleFramework(template.id)"
        >
          {{ template.name }}
        </button>
      </div>
    </template>

    <div
      v-if="composed"
      data-test="template-preview"
      class="mb-3 p-3 rounded-md bg-gray-50 border border-gray-200"
    >
      <p class="text-xs font-medium text-gray-700 mb-2">
        This flow will require {{ composed.requiredAttestations.length }} attestation{{
          composed.requiredAttestations.length === 1 ? '' : 's'
        }}:
      </p>
      <div class="flex flex-wrap gap-1.5">
        <span
          v-for="name in composed.requiredAttestations"
          :key="name"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-800"
        >{{ name }}</span>
      </div>
    </div>

    <div
      v-if="composed && composed.conflicts.length"
      data-test="template-conflicts"
      class="mb-3 p-3 rounded-md bg-amber-50 border border-amber-200 text-xs text-amber-800"
    >
      <p class="font-medium mb-1">These templates disagree:</p>
      <ul class="list-disc list-inside space-y-0.5">
        <li v-for="conflict in composed.conflicts" :key="conflict">{{ conflict }}</li>
      </ul>
    </div>

    <p v-if="error" class="mb-3 text-xs text-red-600">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { composeTemplates, listTemplates, type ComposedTemplate, type HubTemplate } from '../api/hub'

const emit = defineEmits<{
  change: [selection: { templateIds: string[]; requiredAttestations: string[]; templateYaml: string | null }]
}>()

const templates = ref<HubTemplate[]>([])
const selectedServiceType = ref<string | null>(null)
const selectedFrameworks = ref<string[]>([])
const composed = ref<ComposedTemplate | null>(null)
const error = ref('')

const serviceTypeTemplates = computed(() => templates.value.filter(t => t.category === 'SERVICE_TYPE'))
const frameworkTemplates = computed(() => templates.value.filter(t => t.category === 'FRAMEWORK'))

// A service type is the baseline, so exactly one; frameworks stack on top of it.
const selection = computed(() => [
  ...(selectedServiceType.value ? [selectedServiceType.value] : []),
  ...selectedFrameworks.value,
])

function chooseServiceType(id: string) {
  selectedServiceType.value = selectedServiceType.value === id ? null : id
}

function toggleFramework(id: string) {
  selectedFrameworks.value = selectedFrameworks.value.includes(id)
    ? selectedFrameworks.value.filter(f => f !== id)
    : [...selectedFrameworks.value, id]
}

function clear() {
  selectedServiceType.value = null
  selectedFrameworks.value = []
}

watch(selection, async ids => {
  error.value = ''
  if (!ids.length) {
    composed.value = null
    emit('change', { templateIds: [], requiredAttestations: [], templateYaml: null })
    return
  }
  try {
    const { data } = await composeTemplates(ids)
    composed.value = data
    emit('change', {
      templateIds: ids,
      requiredAttestations: data.requiredAttestations,
      templateYaml: data.templateYaml,
    })
  } catch {
    composed.value = null
    error.value = 'Could not preview these templates. You can still create the flow by hand.'
  }
})

onMounted(async () => {
  try {
    const { data } = await listTemplates()
    templates.value = data
  } catch {
    // A flow can always be created by hand; the picker is a convenience.
    error.value = 'Template catalogue unavailable.'
  }
})
</script>
