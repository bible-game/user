package game.bible.user.auth

import game.bible.user.info.InfoService
import game.bible.user.notification.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
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

    @PutMapping("/update-password")
    fun updatePassword(@RequestBody data: PasswordResetData): ResponseEntity<Any> {
        log.info("Update password request received with token [{}]", data.resetToken)
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
        // FixMe :: proper security around who is changing who's password!! (get user from token?!)
    }

    @PostMapping("/request-password-reset")
    fun requestPasswordReset(@RequestParam email: String): ResponseEntity<Any> {
        val user = userInfoService.retrieveByEmail(email)
        if (user == null) {
            return ResponseEntity
                .badRequest()
                .body("No user found with email: $email")

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
