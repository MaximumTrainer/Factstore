import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// Unit and component tests. The Pact consumer specs run separately under
// vitest.config.ts because they need the node environment, not jsdom.
export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
  },
})
