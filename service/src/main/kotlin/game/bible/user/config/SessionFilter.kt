package game.bible.user.config

import game.bible.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.Date

private val log = KotlinLogging.logger {}

/**
 * Rejects tokens issued before the user's password was last changed, so a password reset ends every existing session.
 * Runs after the TokenFilter, which has already verified the token and stored its claims on the request.
 * @since 14th October 2026
 */
class SessionFilter(private val repository: UserRepository) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val claims = req.getAttribute("claims") as? Map<*, *>
        val userId = (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLongOrNull()

        if (claims != null && userId != null) {
            val changedAt = repository.findPasswordChangedAt(userId)?.toInstant()
            val issuedAt = issuedAt(claims["iat"])

            // A token's iat is in whole seconds, so compare at that precision
            if (changedAt != null && (issuedAt == null || issuedAt.epochSecond < changedAt.epochSecond)) {
                log.info { "Rejected a token issued before the password change for user [$userId]" }
                SecurityContextHolder.clearContext()
                res.status = HttpServletResponse.SC_UNAUTHORIZED
                return
            }
        }

        chain.doFilter(req, res)
    }

    private fun issuedAt(iat: Any?): Instant? = when (iat) {
        is Date -> iat.toInstant()
        is Instant -> iat
        is Number -> Instant.ofEpochSecond(iat.toLong())
        else -> null
    }

}
