package game.bible.user.state

import game.bible.common.util.security.TokenManager
import game.bible.user.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/**
 * Exposes State-related Actions
 * @since 24th June 2025
 */
@RestController
@RequestMapping("/state")
class StateController(
    private val service: StateService,
    private val tokenManager: TokenManager
) {

    /** Returns the game state for a given user */
    @GetMapping("/game/")
    fun getGameState(): ResponseEntity<Any> {
        return try {
            val userId = tokenManager.get("user", CurrentUser::class.java).userId
            log.info { "Game state request received for user with id [$userId]" }

            val state = service.retrieveGameState(userId)
            ResponseEntity.status(200).body(state)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }
}

