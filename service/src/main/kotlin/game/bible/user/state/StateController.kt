package game.bible.user.state

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    private val service: StateService
) {

    /** Returns a user's game state */
    @GetMapping("/game")
    fun getGameState(auth: Authentication): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Game state request received for user with id [$userId]" }

            val state = service.retrieveGameState(userId)
            ResponseEntity.status(200).body(state)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }

    /** Registers a user's guess */
    @PostMapping("/guess/{passageId}")
    fun registerGuess(
        auth: Authentication,
        @PathVariable passageId: Long,
        @RequestBody guess: GuessData): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Incoming guess from user with id  [$userId]" }

            val guesses = service.createGuess(userId, passageId, guess)
            ResponseEntity.status(200).body(guesses)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }

}

