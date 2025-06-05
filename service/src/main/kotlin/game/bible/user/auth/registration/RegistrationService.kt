package game.bible.user.auth.registration

import game.bible.user.User
import game.bible.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder

private val log = KotlinLogging.logger {}

/**
 * Registration Service-Logic
 * @since 5th June 2025
 */
@Service
class RegistrationService(
    private val repository: UserRepository,
    private val passwordEncoder: PasswordEncoder) {

    /** Performs user registration */
    @Transactional //@Throws(RegistrationException::class)
    fun register(data: RegistrationData): User {
        val email = data.email // .normalise() -> TODO :: add to common utils (

        if (isExisting(email)) {
            throw Exception("registration.error.user-exists")
            // TODO :: common app exception and message from service-level error bundle
        }

        val password = passwordEncoder.encode(data.password)
        val user = User(email, password)

        log.info { "User registered successfully" }
        return repository.save(user)
    }

    /** Determines if a user exists with a given email */
    @Transactional(readOnly = true)
    fun isExisting(email: String): Boolean {
        log.debug { "Checking if user with email already exists [$email]" }
        return repository.findByEmail(email).isPresent
    }

}