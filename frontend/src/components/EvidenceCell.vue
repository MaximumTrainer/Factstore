<template>
  <div class="text-sm text-gray-500 space-y-1">
    <template v-if="hasEvidence">
      <!-- Uploaded file: downloadable when the vault holds it under a hash. -->
      <div v-if="fileHash" class="flex items-center gap-1">
        <span aria-hidden="true" title="Uploaded file">📎</span>
        <a
          data-test="evidence-file"
          :href="`/api/v1/evidence/${fileHash}`"
          class="text-indigo-600 hover:text-indigo-800 hover:underline break-all"
          >{{ fileLabel }}</a
        >
        <span v-if="fileSize" class="text-xs text-gray-400">({{ fileSize }})</span>
      </div>
      <div v-else-if="attestation.evidenceFileName" class="flex items-center gap-1">
        <span aria-hidden="true" title="Uploaded file">📎</span>
        <span class="break-all">{{ attestation.evidenceFileName }}</span>
        <span v-if="fileSize" class="text-xs text-gray-400">({{ fileSize }})</span>
      </div>

      <!-- Hyperlink evidence held outside the vault. -->
      <div v-for="url in links" :key="url" class="flex items-center gap-1">
        <span aria-hidden="true" title="External link">🔗</span>
        <a
          data-test="evidence-url"
          :href="url"
          target="_blank"
          rel="noopener noreferrer"
          class="text-indigo-600 hover:text-indigo-800 hover:underline break-all"
          :title="url"
          >{{ hostOf(url) }}</a
        >
      </div>

      <!-- Integrity hash, so a reviewer can verify what was stored. -->
      <button
        v-if="fileHash"
        type="button"
        data-test="evidence-hash"
        class="font-mono text-xs text-gray-400 hover:text-gray-600"
        :title="fileHash"
        @click="copyHash"
      >
        {{ shortHash }}<span v-if="copied" class="ml-1 text-green-600">copied</span>
      </button>
    </template>
    <span v-else>—</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Attestation } from '../types'

const props = defineProps<{ attestation: Attestation }>()

const fileHash = computed(() => props.attestation.evidenceFileHash || '')

const fileLabel = computed(
  () => props.attestation.evidenceFileName || shortHash.value || 'Download evidence'
)

const links = computed(() => {
  const urls = [
    ...(props.attestation.evidenceUrl ? [props.attestation.evidenceUrl] : []),
    ...(props.attestation.externalUrls ?? []),
  ]
  return [...new Set(urls)]
})

const hasEvidence = computed(
  () => Boolean(fileHash.value || props.attestation.evidenceFileName) || links.value.length > 0
)

const shortHash = computed(() => (fileHash.value ? fileHash.value.slice(0, 12) : ''))

const fileSize = computed(() => formatBytes(props.attestation.evidenceFileSizeBytes))

function formatBytes(bytes?: number): string {
  if (bytes == null || bytes < 0) return ''
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  const rounded = value >= 10 || Number.isInteger(value) ? Math.round(value) : value.toFixed(1)
  return `${rounded} ${units[unit]}`
}

/** Shows where evidence lives before the reviewer clicks through. */
function hostOf(url: string): string {
  try {
    return new URL(url).host
  } catch {
    return url
  }
}

const copied = ref(false)
async function copyHash() {
  try {
    await navigator.clipboard.writeText(fileHash.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    // Clipboard unavailable (insecure context); the full hash is in the title attribute.
  }
}
</script>
