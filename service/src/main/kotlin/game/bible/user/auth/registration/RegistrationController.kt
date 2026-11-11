package game.bible.user.auth.registration

import game.bible.common.util.security.TokenManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth/register")
class RegistrationController(
    private val service: RegistrationService,
    private val tokenManager: TokenManager,
) {
    @PostMapping
    fun register(@RequestBody data: RegistrationData): ResponseEntity<String> {
        val user = service.register(data)
        return ResponseEntity.status(201).body(tokenManager.generateFor(user.id!!))
    }
}
