package com.factstore

import com.factstore.core.domain.security.Permission
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * A trail for a change authored by a bot.
 *
 * `dependabot[bot]@users.noreply.github.com` is the real author email GitHub puts on a
 * Dependabot commit, and `@field:Email` rejected it: square brackets are not valid in an
 * unquoted local part. So `POST /api/v1/trails` returned `400` and **no evidence could be
 * recorded for any bot-authored change at all** — which is how it broke the dogfood and
 * persona workflows on every Dependabot pull request.
 *
 * That is the wrong trade for a fact store. Automated dependency updates are among the
 * changes a supply-chain record most needs to cover, and "who made this change" is the
 * question the store exists to answer. Refusing the answer because the identity provider
 * chose a format the validator dislikes loses the fact rather than improving it.
 *
 * The address is still validated — it must be a single, whitespace-free `local@domain.tld`,
 * and it is still length-bounded. Only the character class widens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// The scopes the workflows actually hold, granted explicitly: `@WithMockUser` grants
// authorities verbatim, so "admin" here would *not* imply flows:write.
@WithMockUser(
    authorities = [
        Permission.AUTHORITY_PREFIX + "flows:write",
        Permission.AUTHORITY_PREFIX + "trails:write",
    ]
)
class BotAuthoredTrailTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun createFlow(): String {
        val body = mockMvc.post("/api/v1/flows") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"name":"bot-flow-${System.nanoTime()}",
                 "description":"for a bot-authored trail",
                 "requiredAttestationTypes":["backend-tests"]}
            """.trimIndent()
        }.andReturn().response.contentAsString
        return Regex("\"id\" *: *\"([0-9a-f-]{36})\"").find(body)!!.groupValues[1]
    }

    private fun createTrail(email: String) = mockMvc.post("/api/v1/trails") {
        contentType = MediaType.APPLICATION_JSON
        content = """
            {"flowId":"${createFlow()}","gitCommitSha":"abc1234","gitBranch":"main",
             "gitAuthor":"dependabot[bot]","gitAuthorEmail":"$email"}
        """.trimIndent()
    }

    @Test
    fun `a trail can be opened for a commit authored by dependabot`() {
        createTrail("dependabot[bot]@users.noreply.github.com").andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `the author email is recorded exactly as git reports it, not rewritten`() {
        val body = createTrail("dependabot[bot]@users.noreply.github.com")
            .andReturn().response.contentAsString

        // Sanitising it into something that validates would record an author who does not
        // exist, which is worse than not recording one.
        assertTrue(
            body.contains("dependabot[bot]@users.noreply.github.com"),
            "expected the bot address verbatim, got: $body"
        )
    }

    @Test
    fun `github's numeric bot form is accepted too`() {
        createTrail("49699333+dependabot[bot]@users.noreply.github.com").andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `something that is not an address at all is still refused`() {
        createTrail("not-an-address").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an address with whitespace is still refused`() {
        createTrail("two words@example.com").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an address with no domain is still refused`() {
        createTrail("someone@localhost").andExpect { status { isBadRequest() } }
    }
}
