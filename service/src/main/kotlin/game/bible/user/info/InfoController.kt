
package game.bible.user.info

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/**
 * Exposes User Info-related Actions
 * @since 18th July 2025
 */
@RestController
@RequestMapping("/info")
class InfoController(private val service: InfoService) {

    /** Returns user information */
    @GetMapping
    fun getInfo(auth: Authentication): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Info request received for user with id [$userId]" }

            val info = service.retrieveInfo(userId)
            ResponseEntity.status(200).body(info)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }
}
