package game.bible.user.auth

import game.bible.user.User
import game.bible.user.UserRepository
import jakarta.transaction.Transactional
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrElse

/**
 * General Auth Service Logic
 * @since 19th July 2025
 */
@Service
class AuthService(
    private val encoder: PasswordEncoder,
    private val repository: UserRepository) {

    @Transactional
    fun updatePassword(data: PasswordData) {
        val user: User = repository.findByEmail(data.email).getOrElse {
            throw UsernameNotFoundException("User with email ${data.email} not found")
        }
        val newPasswordHash = encoder.encode(data.password)

        repository.updatePassword(user.id!!, newPasswordHash)
    }
}
