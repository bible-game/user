package game.bible.user.auth.login

import game.bible.common.util.security.TokenManager
import game.bible.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.InsufficientAuthenticationException
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
    private val tokenManager: TokenManager
) {

    /** Authenticates a user based on given email and password */
    fun login(data: LoginData): String {
        val email = data.email // .normalise() -> TODO :: add to common utils (

        log.debug { "Attempting authentication for user [$email]" }

        val user = repository.findByEmail(email).getOrElse {
            throw UsernameNotFoundException("User $email not found")
        }

        val authentication = authManager.authenticate(
            UsernamePasswordAuthenticationToken(email, data.password)
        )

        if (authentication.isAuthenticated) {
            return tokenManager.generateFor(user.id!!)

        } else {
            throw InsufficientAuthenticationException("User could not be authenticated! [$email]")
        }
    }

}
