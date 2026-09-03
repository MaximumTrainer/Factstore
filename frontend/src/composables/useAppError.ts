import { ref, readonly } from 'vue'

/**
 * A single app-level error slot, fed by `app.config.errorHandler`.
 *
 * A render error used to tear down the component tree and leave the user on a white
 * screen with nothing but a console message (#157). Surfacing it here means an
 * unexpected failure shows as a banner the user can act on.
 */
const message = ref<string | null>(null)

export function setAppError(error: unknown, context?: string): void {
  const detail = error instanceof Error ? error.message : String(error)
  message.value = context ? `${context}: ${detail}` : detail
}

export function clearAppError(): void {
  message.value = null
}

export function useAppError() {
  return { appError: readonly(message), setAppError, clearAppError }
}
