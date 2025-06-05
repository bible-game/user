package game.bible.user.auth.login

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
@RequestMapping("/auth/login")
class LoginController(private val service: LoginService) {

    /** Logs in and retrieves an authentication token */
    @PostMapping
    fun login(req: HttpServletRequest,
              res: HttpServletResponse,
              @RequestBody data: LoginData): ResponseEntity<Any> {
        return try {
            log.info { "Login request received [${data.email}]" }

            val response = service.login(req, res, data)
            ResponseEntity.ok(response)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }
}

