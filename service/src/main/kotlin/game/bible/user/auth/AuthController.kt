package game.bible.user.auth

import game.bible.user.info.InfoService
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
//    private val notificationService: NotificationService // TODO
) {

    @PutMapping("/update-password")
    fun updatePassword(@RequestBody data: PasswordResetData): ResponseEntity<Any> {
        log.info("Update password request received with token [{}]", data.resetToken)
        authService.updatePassword(data)
        // FixMe :: proper security around who is changing who's password!! (get user from token?!)

        return ResponseEntity.ok().build()
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
            // TODO:
            //  - Generate password reset token, store in DB
            //  - Use email service to send password reset request, including token as query param
            //  - Implement 2FA step (confirm email or enter provided code)
            return ResponseEntity.ok().build()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

}
