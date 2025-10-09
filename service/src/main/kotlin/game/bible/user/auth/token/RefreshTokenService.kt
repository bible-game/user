package game.bible.user.auth.token

import game.bible.common.util.security.TokenManager
import game.bible.config.model.core.SecurityConfig
import game.bible.user.token.RefreshToken
import game.bible.user.token.RefreshToken.Status.*
import game.bible.user.token.RefreshTokenRepository
import io.jsonwebtoken.Claims
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val securityConfig: SecurityConfig,
    private val tokenManager: TokenManager,
) {

    private val authConfig = securityConfig.getJwts()!!["auth"]!!
    private val refreshConfig = securityConfig.getJwts()!!["refresh"]!!

    /**
     * If a valid Refresh Token is available, create a new Auth Token and Refresh Token to renew the current session.
     * To do so, confirm the existence and validity of the REFRESH_TOKEN cookie (using database records as the origin of truth).
     *
     * If valid, the current refresh token will be marked as [ROTATED], and a new Auth/Refresh token pair are generated
     * If invalid or missing, the User will need to log in again. For Refresh Tokens that have expired, the relevant database record
     * will be marked as [EXPIRED].
     *
     * @param request The HTTP Request object to retrieve existing cookies from
     * @param response The HTTP Response object to apply new cookies to
     */
    @Transactional
    fun refreshAuthToken(request: HttpServletRequest, response: HttpServletResponse): Map<String, String> {
        val authCookie = tokenManager.getTokenFrom(request)
        val authClaims = tokenManager.getClaims(authCookie)
        val refreshTokenId = authClaims.subject
        log.debug("Refresh request received for Token ID [$refreshTokenId}]")

        val activeToken = refreshTokenRepository.findByIdOrNull(refreshTokenId)
        if (activeToken != null) {
            val userId = activeToken.userId!!
            log.debug("Refresh Token [ID: $refreshTokenId, User ID: $userId] is valid! Verifying the status...")
            return applyNewTokens(request, response, activeToken, userId)
        } else {
            throw InsufficientAuthenticationException("[Token ID: ${refreshTokenId}] The provided Refresh Token is invalid or doesn't exist!")
        }
    }

    /**
     * Applies a new Refresh Token to the response object, and saves a record to the database for future reference.
     *
     * @param request The HTTP Request object for the original request
     * @param response The HTTP Response object to apply the new cookie to
     * @param userId The User ID to attribute the new Refresh Token to
     * @return Refresh Token JWT String, in case the consumer needs it
     */
    fun applyNewRefreshToken(request: HttpServletRequest, response: HttpServletResponse, userId: Long): String {
        val newRefreshToken = RefreshToken(
            userId = userId,
            status = ACTIVE
        )
        val newRefreshTokenStr = tokenManager.generateFor(newRefreshToken.id, "refresh")
        newRefreshToken.signature = newRefreshTokenStr
        refreshTokenRepository.save(newRefreshToken)
        val refreshCookie = Cookie(refreshConfig.getCookieName(), newRefreshTokenStr).apply {
            domain = securityConfig.getDomainName()
            path = "/"
            isHttpOnly = true
            secure = request.isSecure
            maxAge = refreshConfig.getTimeoutMins()!! * 60
        }
        response.addCookie(refreshCookie)
        return newRefreshTokenStr
    }

    private fun applyNewTokens(
        request: HttpServletRequest,
        response: HttpServletResponse,
        activeToken: RefreshToken,
        userId: Long
    ): Map<String, String> {
        val claims: Claims = tokenManager.getClaims(activeToken.signature)
        if (activeToken.status != ACTIVE || claims.expiration.toInstant() < Instant.now()) {
            throw InsufficientAuthenticationException("[User ID: $userId] The provided Refresh Token has expired. The user will have to log in again.")
        }

        log.debug("Refresh Token [ID: ${activeToken.id}, User ID: ${userId}] has been verified. Building a new Auth/Refresh Token Pair.")
        val newAuthToken = tokenManager.generateFor(userId.toString(), "auth")
        val cookie = Cookie(authConfig.getCookieName(), newAuthToken).apply {
            domain = securityConfig.getDomainName()
            path = "/"
            isHttpOnly = true
            secure = request.isSecure
            maxAge = authConfig.getTimeoutMins()!! * 60
        }
        response.addCookie(cookie)

        activeToken.status = ROTATED
        refreshTokenRepository.save(activeToken)

        val newRefreshToken = applyNewRefreshToken(request, response, userId)
        return mapOf(
            "auth" to newAuthToken,
            "refresh" to newRefreshToken
        )

    }

    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }

}