import { test, expect, type Page } from '@playwright/test'
import { fileURLToPath } from 'url'
import path from 'path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SCREENSHOTS_DIR = path.resolve(__dirname, '../../docs/public/screenshots')

async function screenshot(page: Page, filename: string) {
  await page.screenshot({ path: path.join(SCREENSHOTS_DIR, filename), fullPage: true })
}

/**
 * These run against the dev server with no backend, so the navigation guard's one call has to
 * be answered here.
 *
 * `enforceAuth: false` is the shipped default (#155 rolls enforcement out separately), and it
 * is what the documentation screenshots below should show. The guard treats an *unreachable*
 * config endpoint as enforcing — failing open would show a signed-out visitor a dashboard they
 * may not be entitled to — so without this stub every route correctly bounces to `/login`.
 */
test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/auth/config', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ enforceAuth: false }),
    })
  )
})

test('Dashboard loads', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  await screenshot(page, '01-dashboard.png')
})

test('Flows page loads', async ({ page }) => {
  await page.goto('/flows')
  await expect(page.getByRole('heading', { name: 'Flows' })).toBeVisible()
  await screenshot(page, '02-flows-list.png')
})

test('Assert page has form', async ({ page }) => {
  await page.goto('/assert')
  await expect(page.getByRole('heading', { name: 'Assert Compliance' })).toBeVisible()
  await expect(page.getByPlaceholder('sha256:abc123...')).toBeVisible()
  await screenshot(page, '03-assert-compliance.png')
})

test('Evidence Vault page loads', async ({ page }) => {
  await page.goto('/evidence')
  await expect(page.getByRole('heading', { name: 'Evidence Vault' })).toBeVisible()
  await screenshot(page, '04-evidence-vault.png')
})

test('Environments page loads', async ({ page }) => {
  await page.goto('/environments')
  await expect(page.getByRole('heading', { name: 'Environments' })).toBeVisible()
  await screenshot(page, '05-environments.png')
})

test('Audit Log page loads with filter controls', async ({ page }) => {
  await page.goto('/audit')
  await expect(page.getByRole('heading', { name: 'Audit Log' })).toBeVisible()
  await expect(page.getByText('Event Type')).toBeVisible()
  await expect(page.getByPlaceholder('Filter by actor')).toBeVisible()
  await screenshot(page, '06-audit-log.png')
})

test('Logical Environments page loads', async ({ page }) => {
  await page.goto('/logical-environments')
  await expect(page.getByRole('heading', { name: 'Logical Environments' })).toBeVisible()
  await screenshot(page, '07-logical-environments.png')
})

test('Logical Environments NavBar link navigates correctly', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Logical Envs' }).click()
  await expect(page).toHaveURL(/\/logical-environments/)
  await expect(page.getByRole('heading', { name: 'Logical Environments' })).toBeVisible()
  await screenshot(page, '08-logical-envs-nav.png')
})

test('Policies page loads', async ({ page }) => {
  await page.goto('/policies')
  await expect(page.getByText('Deployment Policies')).toBeVisible()
  await screenshot(page, '09-policies.png')
})

test('Search page loads', async ({ page }) => {
  await page.goto('/search')
  await expect(page.getByRole('heading', { name: 'Search' })).toBeVisible()
  await screenshot(page, '10-search.png')
})

test('Compliance Frameworks page loads', async ({ page }) => {
  await page.goto('/compliance')
  await expect(page.getByRole('heading', { name: 'Compliance Frameworks' })).toBeVisible()
  await screenshot(page, '11-compliance.png')
})

test('Drift Detection page loads', async ({ page }) => {
  await page.goto('/drift')
  await expect(page.getByRole('heading', { name: 'Environment Drift Detection' })).toBeVisible()
  await screenshot(page, '12-drift.png')
})

test('Attestation Types page loads', async ({ page }) => {
  await page.goto('/attestation-types')
  await expect(page.getByRole('heading', { name: 'Attestation Types' })).toBeVisible()
  await screenshot(page, '13-attestation-types.png')
})

test('Compliance Posture page loads', async ({ page }) => {
  await page.goto('/compliance-posture')
  await expect(page.getByRole('heading', { name: 'Compliance Posture' })).toBeVisible()
  await screenshot(page, '14-compliance-posture.png')
})

test('Snapshot Diff page loads', async ({ page }) => {
  await page.goto('/environments/00000000-0000-0000-0000-000000000000/snapshot-diff')
  await expect(page.getByRole('heading', { name: 'Compare Snapshots' })).toBeVisible()
  await screenshot(page, '15-snapshot-diff.png')
})
