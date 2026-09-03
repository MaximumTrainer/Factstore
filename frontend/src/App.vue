<template>
  <div class="min-h-screen bg-gray-50">
    <NavBar />
    <div
      v-if="appError"
      data-test="app-error"
      role="alert"
      class="bg-red-50 border-b border-red-200 px-4 py-3"
    >
      <div class="max-w-7xl mx-auto flex items-start justify-between gap-4">
        <p class="text-sm text-red-800">{{ appError }}</p>
        <button
          type="button"
          class="text-sm text-red-700 hover:text-red-900 font-medium"
          @click="clearAppError"
        >
          Dismiss
        </button>
      </div>
    </div>
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import NavBar from './components/NavBar.vue'
import { useAppError } from './composables/useAppError'

const { appError, clearAppError } = useAppError()
</script>
