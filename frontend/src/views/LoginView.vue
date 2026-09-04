<template>
  <div class="min-h-[70vh] flex items-center justify-center">
    <div class="bg-white rounded-lg shadow p-8 w-full max-w-md">
      <h1 class="text-2xl font-bold text-gray-900 mb-1">Sign in</h1>
      <p class="text-sm text-gray-500 mb-6">OpenFactstore — supply chain compliance</p>

      <!-- A failed or cancelled sign-in says why, without echoing the provider's error
           detail into the address bar. -->
      <p
        v-if="message"
        data-test="login-message"
        role="alert"
        class="mb-4 p-3 rounded-md bg-red-50 border border-red-200 text-sm text-red-800"
      >
        {{ message }}
      </p>

      <form class="mb-4" @submit.prevent="startSso">
        <label class="block text-sm font-medium text-gray-700 mb-1" for="org-slug">
          Organisation
        </label>
        <input
          id="org-slug"
          v-model="orgSlug"
          data-test="org-slug"
          type="text"
          autocomplete="organization"
          placeholder="your-organisation"
          class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <p class="mt-1 text-xs text-gray-500">
          Signs you in with your organisation's identity provider.
        </p>
        <button
          data-test="sso-submit"
          type="submit"
          :disabled="busy || !orgSlug.trim()"
          class="mt-4 w-full bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
        >
          {{ busy ? 'Redirecting…' : 'Continue with SSO' }}
        </button>
      </form>

      <div class="relative my-6">
        <div class="absolute inset-0 flex items-center"><div class="w-full border-t border-gray-200" /></div>
        <div class="relative flex justify-center">
          <span class="bg-white px-2 text-xs text-gray-400">or</span>
        </div>
      </div>

      <a
        data-test="github-login"
        href="/oauth2/authorization/github"
        class="block w-full text-center bg-gray-900 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-gray-800"
      >
        Continue with GitHub
      </a>
      <p class="mt-2 text-xs text-gray-500">
        Available when this deployment has GitHub OAuth configured.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getSsoLoginUrl } from '../api/auth'

const route = useRoute()

const orgSlug = ref('')
const busy = ref(false)
const message = ref('')

const REASONS: Record<string, string> = {
  unauthenticated: 'Please sign in to continue.',
  expired: 'Your session has expired. Please sign in again.',
  failed: 'Sign-in did not complete. Please try again.',
  forbidden: 'That account does not have access to this organisation.',
}

function startSso() {
  const slug = orgSlug.value.trim()
  if (!slug) return
  busy.value = true
  message.value = ''

  // The IdP redirects back to the callback, which sets the session cookie.
  const redirectUri = `${window.location.origin}/api/v1/organisations/${encodeURIComponent(slug)}/sso/callback`

  getSsoLoginUrl(slug, redirectUri)
    .then(({ data }) => {
      window.location.assign(data.loginUrl)
    })
    .catch(() => {
      message.value = `No single sign-on is configured for '${slug}'.`
      busy.value = false
    })
}

onMounted(() => {
  const reason = route.query.reason
  if (typeof reason === 'string' && REASONS[reason]) message.value = REASONS[reason]
  const remembered = route.query.org
  if (typeof remembered === 'string') orgSlug.value = remembered
})
</script>
