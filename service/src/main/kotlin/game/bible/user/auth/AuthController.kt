package game.bible.user.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/**
 * Exposes General Auth-related Actions
 * @since 19th July 2025
 */
@RestController
@RequestMapping("/auth")
class AuthController(private val service: AuthService) {

    @PutMapping("/update-password")
    fun updatePassword(@RequestBody data: PasswordData): ResponseEntity<Any> {
        log.info { "Update password request received [${data.email}]" }
        service.updatePassword(data)
        // FixMe :: proper security around who is changing who's password!! (get user from token?!)

        return ResponseEntity.ok().build()
    }

}
