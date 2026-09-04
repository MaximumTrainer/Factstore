package com.factstore.security

import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.security.Permission
import com.factstore.core.domain.security.RoleModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The shared role model both credential types resolve into (#155 FR-4, consumed by #156 FR-5).
 * These are the rules the whole authorisation story rests on, so they are pinned explicitly.
 */
class RoleModelTest {

    @Test
    fun `a viewer can read but cannot write anything`() {
        val permissions = RoleModel.permissionsFor(MemberRole.VIEWER)

        assertTrue(permissions.contains(Permission.FLOWS_READ))
        assertTrue(permissions.contains(Permission.TRAILS_READ))
        assertTrue(permissions.none { it.scope.endsWith(":write") })
        assertFalse(permissions.contains(Permission.ASSERT_EXECUTE))
        assertFalse(permissions.contains(Permission.ADMIN))
    }

    @Test
    fun `a member can record evidence and run assertions but cannot change definitions`() {
        val permissions = RoleModel.permissionsFor(MemberRole.MEMBER)

        assertTrue(permissions.containsAll(RoleModel.permissionsFor(MemberRole.VIEWER)))
        assertTrue(permissions.contains(Permission.TRAILS_WRITE))
        assertTrue(permissions.contains(Permission.ATTESTATIONS_WRITE))
        assertTrue(permissions.contains(Permission.ASSERT_EXECUTE))
        // Changing what a release is judged against is an administrative act.
        assertFalse(permissions.contains(Permission.FLOWS_WRITE))
        assertFalse(permissions.contains(Permission.POLICIES_WRITE))
        assertFalse(permissions.contains(Permission.ADMIN))
    }

    @Test
    fun `an admin holds everything a member does, plus definition and key management`() {
        val permissions = RoleModel.permissionsFor(MemberRole.ADMIN)

        assertTrue(permissions.containsAll(RoleModel.permissionsFor(MemberRole.MEMBER)))
        assertTrue(permissions.contains(Permission.FLOWS_WRITE))
        assertTrue(permissions.contains(Permission.POLICIES_WRITE))
        assertTrue(permissions.contains(Permission.ADMIN))
    }

    @Test
    fun `the roles are strictly nested, so a higher role never loses a capability`() {
        val viewer = RoleModel.permissionsFor(MemberRole.VIEWER)
        val member = RoleModel.permissionsFor(MemberRole.MEMBER)
        val admin = RoleModel.permissionsFor(MemberRole.ADMIN)

        assertTrue(member.containsAll(viewer))
        assertTrue(admin.containsAll(member))
        assertTrue(admin.size > member.size && member.size > viewer.size)
    }

    @Test
    fun `a service account gets nothing from its role - only from its key scopes`() {
        assertTrue(RoleModel.permissionsFor(MemberRole.SERVICE_ACCOUNT).isEmpty())
    }

    @Test
    fun `authorities carry both the role and one entry per permission`() {
        val authorities = RoleModel.authoritiesFor(MemberRole.ADMIN)

        assertTrue(authorities.contains("ROLE_ADMIN"))
        assertTrue(authorities.contains("SCOPE_flows:write"))
        assertTrue(authorities.contains("SCOPE_admin"))
        assertEquals(RoleModel.permissionsFor(MemberRole.ADMIN).size + 1, authorities.size)
    }

    @Test
    fun `service account authorities come from the scopes given, not from the role`() {
        val authorities = RoleModel.authoritiesForScopes(setOf(Permission.ATTESTATIONS_WRITE))

        assertTrue(authorities.contains("ROLE_SERVICE_ACCOUNT"))
        assertTrue(authorities.contains("SCOPE_attestations:write"))
        assertFalse(authorities.contains("SCOPE_flows:write"))
    }

    @Test
    fun `a first sign-in with no membership is a viewer, never privileged`() {
        assertEquals(MemberRole.VIEWER, RoleModel.DEFAULT_ROLE_FOR_NEW_MEMBER)
        assertFalse(
            RoleModel.permissionsFor(RoleModel.DEFAULT_ROLE_FOR_NEW_MEMBER).contains(Permission.ADMIN)
        )
    }

    // --- Scope parsing ----------------------------------------------------

    @Test
    fun `scopes round-trip through their wire form`() {
        Permission.entries.forEach { permission ->
            assertEquals(permission, Permission.fromScope(permission.scope))
        }
    }

    @Test
    fun `scope parsing is case and whitespace tolerant`() {
        assertEquals(Permission.FLOWS_READ, Permission.fromScope("  FLOWS:READ "))
    }

    @Test
    fun `an unknown scope is reported rather than silently dropped`() {
        val parsed = Permission.parse(listOf("flows:read", "flows:destroy", " "))

        assertEquals(setOf(Permission.FLOWS_READ), parsed.permissions)
        assertEquals(setOf("flows:destroy"), parsed.unknown)
    }

    @Test
    fun `an unknown scope resolves to null rather than a default`() {
        assertNull(Permission.fromScope("everything"))
    }

    @Test
    fun `the CI preset can write evidence and assert, but cannot administer anything`() {
        val preset = Permission.CI_PIPELINE_PRESET

        assertTrue(preset.contains(Permission.ATTESTATIONS_WRITE))
        assertTrue(preset.contains(Permission.ASSERT_EXECUTE))
        assertFalse(preset.contains(Permission.ADMIN))
        assertFalse(preset.contains(Permission.FLOWS_WRITE))
    }

    @Test
    fun `the default for a credential that requests no scopes is read-only`() {
        assertTrue(Permission.DEFAULT_MINIMAL.all { it.scope.endsWith(":read") })
    }
}
