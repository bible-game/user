package game.bible.user.auth

import game.bible.user.info.InfoService
import game.bible.user.notification.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Exposes General Auth-related Actions
 * @since 19th July 2025
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val userInfoService: InfoService,
    private val notificationService: NotificationService,
) {

    @GetMapping("/validate-token")
    fun validateToken(@RequestParam token: String): ResponseEntity<Map<String, Any?>> {
        log.debug("Validating password reset token")
        return ResponseEntity.ok(
            mapOf("result" to authService.isTokenValid(token))
        )
    }

    @PutMapping("/update-password")
    fun updatePassword(@RequestBody data: PasswordResetData): ResponseEntity<Any> {
        log.debug("Password update request received")
        return try {
            authService.updatePassword(data)
            ResponseEntity.ok().build()
        } catch (isE: IllegalStateException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "type" to "CLIENT_ERROR",
                    "message" to isE.message,
                )
            )
        } catch (e: Exception) {
            log.error("Error while updating password", e)
            ResponseEntity.internalServerError().body(
                mapOf(
                    "type" to "INTERNAL_SERVER_ERROR",
                    "message" to "Internal Server Error. Please check the logs for detail."
                )
            )
        }
    }

    @PostMapping("/request-password-reset")
    fun requestPasswordReset(@RequestParam email: String): ResponseEntity<Any> {
        val user = userInfoService.retrieveByEmail(email)
        if (user == null) {
            log.info("Password reset requested for an email with no account")
            return ResponseEntity.ok().build()

        } else {
            val token = authService.createPasswordResetToken(user)
            return try {
                notificationService.sendResetRequest(email, token)
                ResponseEntity.ok().build()
            } catch (e: Exception) {
                log.error("Error occurred when sending reset request email!", e)
                ResponseEntity.internalServerError().body("An unexpected error occurred. Please review the service logs.")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

}
