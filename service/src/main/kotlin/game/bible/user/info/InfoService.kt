
package game.bible.user.info

import game.bible.user.User
import game.bible.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.sql.Timestamp
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
            "id" to user.id!!,
            "firstname" to user.firstname,
            "lastname" to user.lastname,
            "profileCompleted" to (user.profileCompletedAt != null && user.firstname.isNotBlank() && user.lastname.isNotBlank())
        )
    }

    /** Names are required; existing church records are preserved. Concurrent requests serialize on the user row. */
    @Transactional
    fun completeProfile(userId: Long, data: ProfileData): Map<String, Any> {
        val user = repository.findForClerkMigration(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        if (user.profileCompletedAt == null || user.firstname.isBlank() || user.lastname.isBlank()) {
            val first = user.firstname.trim().ifBlank { data.firstname?.trim().orEmpty() }
            val last = user.lastname.trim().ifBlank { data.lastname?.trim().orEmpty() }
            if (first.isBlank() || last.isBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "First and last names are required")
            }
            user.firstname = first
            user.lastname = last
            user.profileCompletedAt = Timestamp(System.currentTimeMillis())
            repository.saveAndFlush(user)
        }
        return retrieveInfo(userId)
    }

    fun retrieveByEmail(email: String): User? {
        log.info { "Retrieving User data associated with email [$email]" }

        return repository.findByEmail(email).getOrNull()
    }

}
