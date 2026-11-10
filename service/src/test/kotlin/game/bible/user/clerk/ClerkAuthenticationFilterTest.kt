package game.bible.user.clerk

import game.bible.user.User
import game.bible.user.UserRepository
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.sql.Timestamp
import java.time.Instant
import java.util.Optional

class ClerkAuthenticationFilterTest {
    private val decoder = mock(JwtDecoder::class.java)
    private val repository = mock(UserRepository::class.java)
    private val client = mock(ClerkBackendClient::class.java)
    private val store = mock(ClerkAccountStore::class.java)
    private val resolver = ClerkAccountResolver(repository, client, store)
    private val filter = ClerkAuthenticationFilter(decoder, resolver)

    @AfterEach
    fun cleanup() { SecurityContextHolder.clearContext() }

    @Test
    fun `passes existing numeric ID to controllers and clears context afterwards`() {
        val jwt = Jwt.withTokenValue("valid").header("alg", "RS256").subject("user_abc")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build()
        `when`(decoder.decode("valid")).thenReturn(jwt)
        val user = User().also { it.id = 47L; it.clerkUserId = "user_abc"; it.clerkEmailVerifiedAt = Timestamp(1) }
        `when`(repository.findByClerkUserId("user_abc")).thenReturn(Optional.of(user))
        var called = false
        filter.doFilter(request("/info", "Bearer valid"), MockHttpServletResponse(), FilterChain { _, _ ->
            called = true
            assertEquals("47", SecurityContextHolder.getContext().authentication.principal)
        })
        assertTrue(called)
        assertNull(SecurityContextHolder.getContext().authentication)
        verifyNoInteractions(client, store)
    }

    @Test
    fun `first request provisions without waiting for webhooks`() {
        val jwt = Jwt.withTokenValue("new").header("alg", "RS256").subject("user_new").build()
        `when`(decoder.decode("new")).thenReturn(jwt)
        `when`(repository.findByClerkUserId("user_new")).thenReturn(Optional.empty())
        val identity = ClerkIdentity("user_new", "new@example.com", "New", "Player", null)
        `when`(client.retrieve("user_new")).thenReturn(identity)
        `when`(store.provision(identity)).thenReturn(81L)
        filter.doFilter(request("/state/game", "Bearer new"), MockHttpServletResponse(), FilterChain { _, _ ->
            assertEquals("81", SecurityContextHolder.getContext().authentication.principal)
        })
        verify(store).provision(identity)
    }

    @Test
    fun `invalid token is 401 and does not provision`() {
        `when`(decoder.decode("invalid")).thenThrow(BadJwtException("bad signature"))
        val response = MockHttpServletResponse()
        filter.doFilter(request("/info", "Bearer invalid"), response, FilterChain { _, _ -> fail<Unit>("must not reach controller") })
        assertEquals(401, response.status)
        verifyNoInteractions(repository, client, store)
    }

    @Test
    fun `unverified migrated user receives 403 and is not provisioned`() {
        val jwt = Jwt.withTokenValue("new").header("alg", "RS256").subject("user_new").build()
        `when`(decoder.decode("new")).thenReturn(jwt)
        `when`(repository.findByClerkUserId("user_new")).thenReturn(Optional.empty())
        `when`(client.retrieve("user_new")).thenThrow(ClerkAccountException(org.springframework.http.HttpStatus.FORBIDDEN, "email_verification_required"))
        val response = MockHttpServletResponse()
        filter.doFilter(request("/info", "Bearer new"), response, FilterChain { _, _ -> fail<Unit>("must not reach controller") })
        assertEquals(403, response.status)
        assertTrue(response.contentAsString.contains("email_verification_required"))
        verifyNoInteractions(store)
    }

    @Test
    fun `legacy registration is 404 and public leaderboard needs no token`() {
        val response = MockHttpServletResponse()
        filter.doFilter(request("/auth/register", null, "POST"), response, FilterChain { _, _ -> fail<Unit>("legacy auth must be closed") })
        assertEquals(404, response.status)
        var called = false
        filter.doFilter(request("/leader/top"), MockHttpServletResponse(), FilterChain { _, _ -> called = true })
        assertTrue(called)
        verifyNoInteractions(decoder, repository, client, store)
    }

    private fun request(path: String, token: String? = null, method: String = "GET") = MockHttpServletRequest(method, path).also {
        it.servletPath = path
        if (token != null) it.addHeader("Authorization", token)
    }
}
