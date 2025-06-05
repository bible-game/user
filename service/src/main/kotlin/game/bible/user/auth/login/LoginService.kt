package game.bible.user.auth.login

import game.bible.common.util.security.TokenManager
import game.bible.config.model.core.SecurityConfig
import game.bible.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrElse

private val log = KotlinLogging.logger {}

/**
 * Login Service-Logic
 * @since 5th June 2025
 */
@Service
class LoginService(
    private val repository: UserRepository,
    private val authManager: AuthenticationManager,
    private val tokenManager: TokenManager,
    private val securityConfig: SecurityConfig
) {

    /** Authenticates a user based on given email and password */
    fun login(
        req: HttpServletRequest, res: HttpServletResponse, data: LoginData): String {
        val email = data.email // .normalise() -> TODO :: add to common utils (

        log.debug { "Attempting authentication for user [$email]" }

        val user = repository.findByEmail(email).getOrElse {
            throw UsernameNotFoundException("User $email not found")
        }

        val authentication = authManager.authenticate(
            UsernamePasswordAuthenticationToken(email, data.password)
        )

        if (authentication.isAuthenticated) {
            clearSessionCookies(req, res)

            val token: String = tokenManager.generateFor(user.id!!)
            applyJWTCookie(req, res, token)

            return token

        } else {
            throw Exception("User could not be authenticated! [$email]")
        }
    }

    /** Clears session cookies from the response */
    private fun clearSessionCookies(request: HttpServletRequest, response: HttpServletResponse) {
        val cookies = request.cookies
        if (cookies != null) {
            log.debug { "Found [${cookies.size}] cookies ..." }

            for (cookie in cookies) {
                if (cookie.name.endsWith(securityConfig.getJwt()!!.getCookieName() as String, false)) {
                    log.debug("... Clearing cookie [{}] ...", cookie.name)
                    cookie.value = null
                    cookie.domain = securityConfig.getJwt()!!.getCookieDomain()!!
                    cookie.maxAge = 0 // Don't set to -1, or it will become a session cookie!
                    response.addCookie(cookie)
                }
            }
        }
    }

    /** Adds a JWT as a cookie to the response */
    fun applyJWTCookie(request: HttpServletRequest, response: HttpServletResponse, token: String) {
        val cookieName = securityConfig.getJwt()!!.getCookieName()!!
        val timeout = securityConfig.getJwt()!!.getSessionTimeoutMins()!! * 60
        val origin = request.getHeader("origin")
        val cookieDomain = if (origin != null && origin.contains("localhost")) {
            "localhost"
        } else {
            securityConfig.getJwt()!!.getCookieDomain()!!
        }

        clearSessionCookies(request, response)
        log.debug { "Deleting cookie: [$cookieName]" }

        val cookie = Cookie(cookieName, token).apply {
            domain = cookieDomain
            path = "/"
            isHttpOnly = false
            secure = request.isSecure
            maxAge = timeout
        }

        log.debug { "Adding cookie [$cookieName] to domain [$cookieDomain]" }
        response.addCookie(cookie)
    }

}