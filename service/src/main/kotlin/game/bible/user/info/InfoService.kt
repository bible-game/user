
package game.bible.user.info

import game.bible.user.User
import game.bible.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger {}

/**
 * User Info Service-Logic
 * @since 18th July 2025
 */
@Service
class InfoService(private val repository: UserRepository) {

    /** Retrieves the information for a given user */
    fun retrieveInfo(userId: Long): Map<String, Any> {
        log.info { "Retrieving user information [$userId]" }
        val user = repository.findById(userId).getOrElse {
            throw Exception()
        }

        return mapOf(
            "firstname" to user.firstname,
            "lastname" to user.lastname
        )
    }

    fun retrieveByEmail(email: String): User? {
        log.info { "Retrieving User data associated with email [$email]" }

        return repository.findByEmail(email).getOrNull()
    }

}
