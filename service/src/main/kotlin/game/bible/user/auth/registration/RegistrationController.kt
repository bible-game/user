package game.bible.user.auth.registration

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/**
 * Exposes Registration-related Actions
 * @since 5th June 2025
 */
@RestController
@RequestMapping("/auth/register")
class RegistrationController(private val service: RegistrationService) {

    /** Registers a new user */
    @PostMapping
    fun register(@RequestBody data: RegistrationData): ResponseEntity<Any> {
        return try {
            log.info { "Registration request received [${data.email}]" }

            val response = service.register(data)
            ResponseEntity.ok(response)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }
}

