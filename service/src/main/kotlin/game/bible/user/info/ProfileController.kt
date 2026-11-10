package game.bible.user.info

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class ProfileData(
    @field:Size(max = 255) val firstname: String? = null,
    @field:Size(max = 255) val lastname: String? = null
)

@RestController
@RequestMapping("/info/profile")
@ConditionalOnProperty(prefix = "clerk", name = ["enabled"], havingValue = "true")
class ProfileController(private val service: InfoService) {
    @PutMapping
    fun complete(auth: Authentication, @Valid @RequestBody data: ProfileData): Map<String, Any> {
        val id = (auth.principal as? String)?.toLongOrNull()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return service.completeProfile(id, data)
    }
}
