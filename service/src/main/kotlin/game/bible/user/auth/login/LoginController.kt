package game.bible.user.auth.login

import game.bible.user.auth.token.RefreshTokenService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
class LoginController(
    private val loginService: LoginService,
    private val refreshTokenService: RefreshTokenService,
) {

    /** Logs in and retrieves an authentication/refresh token pair */
    @PostMapping
    fun login(
        req: HttpServletRequest,
        res: HttpServletResponse,
        @RequestBody data: LoginData
    ): ResponseEntity<Any> {
        log.info { "Login request received [${data.email}]" }

        return ResponseEntity.ok(loginService.login(req, res, data))
    }

    @GetMapping("/renew-token")
    fun renewToken(
        req: HttpServletRequest,
        res: HttpServletResponse,
    ): ResponseEntity<Any> {
        return ResponseEntity<Map<String, String>>.ok(refreshTokenService.refreshAuthToken(req, res))
    }

}

