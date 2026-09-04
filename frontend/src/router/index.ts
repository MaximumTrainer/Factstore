import { createRouter, createWebHistory } from 'vue-router'
import { clearPrincipal, loadPrincipal } from '../composables/useAuth'
import { configureAuthHandling } from '../api/client'
import DashboardView from '../views/DashboardView.vue'
import FlowsView from '../views/FlowsView.vue'
import FlowDetailView from '../views/FlowDetailView.vue'
import TrailDetailView from '../views/TrailDetailView.vue'
import AssertView from '../views/AssertView.vue'
import EvidenceVaultView from '../views/EvidenceVaultView.vue'
import SecureVaultView from '../views/SecureVaultView.vue'
import IntegrationsView from '../views/IntegrationsView.vue'
import AtlassianIntegrationsView from '../views/AtlassianIntegrationsView.vue'
import EnvironmentsView from '../views/EnvironmentsView.vue'
import EnvironmentDetailView from '../views/EnvironmentDetailView.vue'
import LogicalEnvironmentsView from '../views/LogicalEnvironmentsView.vue'
import LogicalEnvironmentDetailView from '../views/LogicalEnvironmentDetailView.vue'
import AuditLogView from '../views/AuditLogView.vue'
import LedgerView from '../views/LedgerView.vue'
import SearchView from '../views/SearchView.vue'
import NotificationsView from '../views/NotificationsView.vue'
import NotificationRulesView from '../views/NotificationRulesView.vue'
import SsoConfigView from '../views/SsoConfigView.vue'
import ComplianceView from '../views/ComplianceView.vue'
import PoliciesView from '../views/PoliciesView.vue'
import DriftView from '../views/DriftView.vue'
import CompliancePostureView from '../views/CompliancePostureView.vue'
import SnapshotDiffView from '../views/SnapshotDiffView.vue'
import AttestationTypesView from '../views/AttestationTypesView.vue'
import MetricsView from '../views/MetricsView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/flows', component: FlowsView },
    { path: '/flows/:id', component: FlowDetailView },
    { path: '/trails/:id', component: TrailDetailView },
    { path: '/assert', component: AssertView },
    { path: '/evidence', component: EvidenceVaultView },
    { path: '/vault', component: SecureVaultView },
    { path: '/search', component: SearchView },
    { path: '/integrations', component: IntegrationsView },
    { path: '/integrations/atlassian', component: AtlassianIntegrationsView },
    { path: '/integrations/sso', component: SsoConfigView },
    { path: '/environments', component: EnvironmentsView },
    { path: '/environments/:id', component: EnvironmentDetailView },
    { path: '/logical-environments', component: LogicalEnvironmentsView },
    { path: '/logical-environments/:id', component: LogicalEnvironmentDetailView },
    { path: '/audit', component: AuditLogView },
    { path: '/ledger', component: LedgerView },
    { path: '/notifications', component: NotificationsView },
    { path: '/notifications/rules', component: NotificationRulesView },
    { path: '/compliance', component: ComplianceView },
    { path: '/compliance-posture', component: CompliancePostureView },
    { path: '/policies', component: PoliciesView },
    { path: '/drift', component: DriftView },
    { path: '/environments/:id/snapshot-diff', component: SnapshotDiffView },
    { path: '/attestation-types', component: AttestationTypesView },
    { path: '/metrics', component: MetricsView },
    // The one route that must be reachable without being signed in.
    { path: '/login', component: LoginView, meta: { public: true } }
  ]
})

/**
 * Navigation guard (#156 FR-6.3): every route except `/login` requires a session.
 *
 * The intended destination is preserved so sign-in lands where the user was going, rather
 * than dumping them on the dashboard.
 */
router.beforeEach(async to => {
  if (to.meta.public) return true

  const principal = await loadPrincipal()
  if (principal) return true

  return {
    path: '/login',
    query: {
      redirect: to.fullPath,
      reason: 'unauthenticated',
    },
  }
})

// Give the API client a way to bounce a 401 without importing the router (which imports the
// views, which import the client).
configureAuthHandling({
  redirect: path => {
    if (router.currentRoute.value.fullPath !== path) router.push(path)
  },
  onUnauthenticated: clearPrincipal,
})

export default router
