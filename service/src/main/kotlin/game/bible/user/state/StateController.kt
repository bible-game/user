package game.bible.user.state

import game.bible.user.state.data.GuessData
import game.bible.user.state.data.ReadData
import game.bible.user.state.data.ReviewData
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

    /** Returns a user's read state */
    @GetMapping("/read")
    fun getReadState(auth: Authentication): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Read state request received for user with id [$userId]" }

            val state = service.retrieveReadState(userId)
            ResponseEntity.status(200).body(state)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }

    /** Returns a user's review state */
    @GetMapping("/review")
    fun getReviewState(auth: Authentication): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Review state request received for user with id [$userId]" }

            val state = service.retrieveReviewState(userId)
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
        @RequestBody data: GuessData
    ): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Incoming guess from user with id  [$userId]" }

            val guesses = service.createGuess(userId, passageId, data)
            ResponseEntity.status(200).body(guesses)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }

    /** Registers a user's ticked read */
    @PostMapping("/read")
    fun registerRead(
        auth: Authentication,
        @RequestBody data: ReadData
    ): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Incoming read from user with id  [$userId]" }

            val reads = service.createRead(userId, data)
            ResponseEntity.status(200).body(reads)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }

    /** Registers a user's review */
    @PostMapping("/review")
    fun registerReview(
        auth: Authentication,
        @RequestBody data: ReviewData
    ): ResponseEntity<Any> {
        return try {
            val userId = (auth.principal as String).toLongOrNull()
                ?: throw Exception()

            log.info { "Incoming review from user with id  [$userId]" }

            val reads = service.createReview(userId, data)
            ResponseEntity.status(200).body(reads)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }

}

