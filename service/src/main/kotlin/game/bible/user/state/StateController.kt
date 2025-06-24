package game.bible.user.state

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/**
 * Exposes State-related Actions
 * @since 24th June 2025
 */
@RestController
@RequestMapping("/state")
class StateController(private val service: StateService) {

    /** Returns the game state for a given user */
    @GetMapping("/game/{userId}")
    fun getGameState(@PathVariable userId: Long): ResponseEntity<Any> {
        return try {
            log.info { "Game state request received for user with id [$userId]" }

            val state = service.retrieveGameState(userId)
            ResponseEntity.status(200).body(state)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }
}

