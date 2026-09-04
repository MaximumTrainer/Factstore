<template>
  <div v-if="principal" class="relative" data-test="user-menu">
    <button
      data-test="user-menu-button"
      type="button"
      class="flex items-center gap-2 px-3 py-1.5 rounded-md text-sm text-gray-700 hover:bg-gray-100"
      :aria-expanded="open"
      aria-haspopup="true"
      @click="open = !open"
    >
      <span
        class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-indigo-600 text-white text-xs font-semibold"
        aria-hidden="true"
        >{{ initials }}</span
      >
      <span class="hidden sm:inline">{{ displayName }}</span>
      <span
        v-if="principal.role"
        data-test="user-role"
        class="hidden sm:inline px-1.5 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600"
        >{{ principal.role }}</span
      >
    </button>

    <div
      v-if="open"
      data-test="user-menu-panel"
      class="absolute right-0 mt-2 w-72 bg-white rounded-md shadow-lg border border-gray-200 z-50"
    >
      <div class="px-4 py-3 border-b border-gray-100">
        <p class="text-sm font-medium text-gray-900 truncate">{{ principal.name || principal.email }}</p>
        <p class="text-xs text-gray-500 truncate">{{ principal.email }}</p>
      </div>

      <div v-if="principal.organisations.length" class="px-4 py-3 border-b border-gray-100">
        <p class="text-xs font-medium text-gray-500 mb-2">Organisation</p>
        <!-- Every request is scoped to the active organisation, so switching is an explicit,
             visible act rather than a hidden filter. -->
        <ul class="space-y-1">
          <li v-for="org in principal.organisations" :key="org.orgSlug">
            <button
              data-test="org-option"
              type="button"
              :disabled="switching"
              class="w-full flex items-center justify-between gap-2 px-2 py-1 rounded text-sm hover:bg-gray-50 disabled:opacity-50"
              :class="org.orgSlug === principal.orgSlug ? 'text-indigo-700 font-medium' : 'text-gray-700'"
              @click="select(org.orgSlug)"
            >
              <span class="truncate">{{ org.orgSlug }}</span>
              <span class="shrink-0 text-xs text-gray-400">{{ org.role }}</span>
            </button>
          </li>
        </ul>
        <p v-if="switchError" class="mt-2 text-xs text-red-600">{{ switchError }}</p>
      </div>

      <div class="px-4 py-3 border-b border-gray-100">
        <p class="text-xs font-medium text-gray-500 mb-1">Permissions</p>
        <div class="flex flex-wrap gap-1">
          <span
            v-for="permission in principal.permissions"
            :key="permission"
            class="px-1.5 py-0.5 rounded text-xs bg-gray-100 text-gray-600 font-mono"
            >{{ permission }}</span
          >
          <span v-if="!principal.permissions.length" class="text-xs text-gray-400">none</span>
        </div>
      </div>

      <button
        data-test="sign-out"
        type="button"
        class="w-full text-left px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50"
        @click="doSignOut"
      >
        Sign out
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { principal, displayName, signOut, setActiveOrganisation } = useAuth()

const open = ref(false)
const switching = ref(false)
const switchError = ref('')

const initials = computed(() => {
  const source = principal.value?.name || principal.value?.email || '?'
  return source
    .split(/[\s@.]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(part => part[0]!.toUpperCase())
    .join('')
})

async function select(orgSlug: string) {
  if (orgSlug === principal.value?.orgSlug) {
    open.value = false
    return
  }
  switching.value = true
  switchError.value = ''
  try {
    await setActiveOrganisation(orgSlug)
    open.value = false
    // Reload the current view against the new scope rather than showing the old
    // organisation's data under the new name.
    router.go(0)
  } catch {
    switchError.value = 'Could not switch organisation.'
  } finally {
    switching.value = false
  }
}

async function doSignOut() {
  await signOut()
  open.value = false
  router.push('/login')
}
</script>
