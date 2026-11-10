package game.bible.user.auth.login

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/**
 * Exposes Login-related Actions
 * @since 5th June 2025
 */
@RestController
@ConditionalOnProperty(prefix = "clerk", name = ["enabled"], havingValue = "false", matchIfMissing = true)
@RequestMapping("/auth/login")
class LoginController(private val service: LoginService) {

    /** Logs in and retrieves an authentication token */
    @PostMapping
    fun login(@RequestBody data: LoginData): ResponseEntity<Any> {
        log.info { "Login request received [${data.email}]" }

        val response = service.login(data)
        return ResponseEntity.ok(response)

    }
}

