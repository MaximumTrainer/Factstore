<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Attestation Types</h1>
        <p class="mt-1 text-sm text-gray-500">Manage custom attestation types for your organization.</p>
      </div>
      <button
        class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
        @click="openCreate"
      >
        + New Type
      </button>
    </div>

    <!-- Include archived toggle -->
    <div class="flex items-center gap-2 mb-4">
      <input
        id="showArchived"
        v-model="showArchived"
        type="checkbox"
        class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
        @change="loadTypes"
      />
      <label for="showArchived" class="text-sm text-gray-600">Show archived types</label>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading…</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>
    <div v-if="actionError" class="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md text-sm">{{ actionError }}</div>
    <div v-if="!loading && !loadError && types.length === 0" class="text-center text-gray-500 py-12">No attestation types found.</div>
    <div v-else class="bg-white shadow rounded-lg overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Description</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Version</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <template v-for="t in types" :key="t.id">
            <!-- View row -->
            <tr v-if="editingId !== t.id" :class="t.archivedAt ? 'opacity-60' : ''" class="hover:bg-gray-50">
              <td class="px-6 py-3 text-sm font-medium text-gray-900">{{ t.name }}</td>
              <td class="px-6 py-3 text-sm text-gray-600">{{ t.description }}</td>
              <td class="px-6 py-3 text-sm text-gray-500">v{{ t.version }}</td>
              <td class="px-6 py-3 text-sm">
                <span
                  :class="t.archivedAt ? 'bg-gray-100 text-gray-600' : 'bg-green-100 text-green-700'"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                >
                  {{ t.archivedAt ? 'Archived' : 'Active' }}
                </span>
              </td>
              <td class="px-6 py-3 text-sm flex gap-3">
                <button
                  class="text-indigo-600 hover:text-indigo-900 font-medium"
                  @click="startEdit(t)"
                >Edit</button>
                <button
                  v-if="!t.archivedAt"
                  class="text-yellow-600 hover:text-yellow-900 font-medium"
                  :disabled="actionLoading === t.id"
                  @click="doArchive(t.id)"
                >{{ actionLoading === t.id ? '…' : 'Archive' }}</button>
                <button
                  v-else
                  class="text-green-600 hover:text-green-900 font-medium"
                  :disabled="actionLoading === t.id"
                  @click="doUnarchive(t.id)"
                >{{ actionLoading === t.id ? '…' : 'Unarchive' }}</button>
              </td>
            </tr>
            <!-- Inline edit row -->
            <tr v-else class="bg-indigo-50">
              <td class="px-6 py-3 text-sm font-medium text-gray-900">{{ t.name }}</td>
              <td class="px-6 py-3" colspan="2">
                <input
                  v-model="editDescription"
                  type="text"
                  class="w-full border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="Description"
                />
              </td>
              <td class="px-6 py-3"></td>
              <td class="px-6 py-3 flex gap-2">
                <button
                  class="bg-indigo-600 text-white px-3 py-1 rounded text-sm hover:bg-indigo-700 disabled:opacity-50"
                  :disabled="saving"
                  @click="saveEdit(t.id)"
                >{{ saving ? 'Saving…' : 'Save' }}</button>
                <button
                  class="text-gray-500 hover:text-gray-700 text-sm"
                  @click="cancelEdit"
                >Cancel</button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Create modal -->
    <div v-if="showCreateModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 class="text-lg font-bold text-gray-900 mb-4">New Attestation Type</h2>
        <form @submit.prevent="doCreate">
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
            <input
              v-model="createForm.name"
              type="text"
              required
              placeholder="e.g. SECURITY_SCAN"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <input
              v-model="createForm.description"
              type="text"
              required
              placeholder="Describe this attestation type"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Org Slug (optional)</label>
            <input
              v-model="createForm.orgSlug"
              type="text"
              placeholder="my-org"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <p v-if="createError" class="mb-3 text-sm text-red-600">{{ createError }}</p>
          <div class="flex justify-end gap-3">
            <button
              type="button"
              class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
              @click="closeCreate"
            >Cancel</button>
            <button
              type="submit"
              :disabled="creating"
              class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            >{{ creating ? 'Creating…' : 'Create' }}</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  listAttestationTypes,
  createAttestationType,
  updateAttestationType,
  archiveAttestationType,
  unarchiveAttestationType
} from '../api/attestationTypes'
import type { CustomAttestationType } from '../api/attestationTypes'

const types = ref<CustomAttestationType[]>([])
const loading = ref(true)
const loadError = ref('')
const showArchived = ref(false)

const editingId = ref<string | null>(null)
const editDescription = ref('')
const saving = ref(false)

const actionLoading = ref<string | null>(null)
const actionError = ref('')

const showCreateModal = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = ref({ name: '', description: '', orgSlug: '' })

async function loadTypes() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listAttestationTypes(showArchived.value)
    types.value = res.data
  } catch (err) {
    console.error('Failed to load attestation types', err)
    loadError.value = 'Failed to load attestation types.'
  } finally {
    loading.value = false
  }
}

function startEdit(t: CustomAttestationType) {
  editingId.value = t.id
  editDescription.value = t.description
}

function cancelEdit() {
  editingId.value = null
  editDescription.value = ''
}

async function saveEdit(id: string) {
  saving.value = true
  actionError.value = ''
  try {
    const res = await updateAttestationType(id, { description: editDescription.value })
    const idx = types.value.findIndex(t => t.id === id)
    if (idx !== -1) types.value[idx] = res.data
    cancelEdit()
  } catch (err) {
    console.error('Failed to update attestation type', err)
    actionError.value = 'Failed to save changes. Please try again.'
  } finally {
    saving.value = false
  }
}

async function doArchive(id: string) {
  actionLoading.value = id
  actionError.value = ''
  try {
    const res = await archiveAttestationType(id)
    const idx = types.value.findIndex(t => t.id === id)
    if (idx !== -1) types.value[idx] = res.data
  } catch (err) {
    console.error('Failed to archive attestation type', err)
    actionError.value = 'Failed to archive attestation type. Please try again.'
  } finally {
    actionLoading.value = null
  }
}

async function doUnarchive(id: string) {
  actionLoading.value = id
  actionError.value = ''
  try {
    const res = await unarchiveAttestationType(id)
    const idx = types.value.findIndex(t => t.id === id)
    if (idx !== -1) types.value[idx] = res.data
  } catch (err) {
    console.error('Failed to unarchive attestation type', err)
    actionError.value = 'Failed to unarchive attestation type. Please try again.'
  } finally {
    actionLoading.value = null
  }
}

function openCreate() {
  createForm.value = { name: '', description: '', orgSlug: '' }
  createError.value = ''
  showCreateModal.value = true
}

function closeCreate() {
  showCreateModal.value = false
  createError.value = ''
}

async function doCreate() {
  creating.value = true
  createError.value = ''
  try {
    const req: { name: string; description: string; orgSlug?: string } = {
      name: createForm.value.name,
      description: createForm.value.description
    }
    if (createForm.value.orgSlug) req.orgSlug = createForm.value.orgSlug
    const res = await createAttestationType(req)
    types.value.push(res.data)
    closeCreate()
  } catch (err) {
    console.error('Failed to create attestation type', err)
    createError.value = 'Failed to create attestation type. Please try again.'
  } finally {
    creating.value = false
  }
}

onMounted(loadTypes)
</script>
