package game.bible.user.guess

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Date

private val log = KotlinLogging.logger {}

/**
 * Exposes Guess-related Actions
 *
 * @author J. R. Smith
 * @since 21st January 2025
 */
@RestController
@RequestMapping("/guess")
class GuessController(private val service: GuessService) {

    /** Returns guess 'closeness' for a given user */
    @GetMapping("/{date}/{book}/{chapter}")
    fun getCloseness(
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") date: Date,
        @PathVariable book: String, @PathVariable chapter: String
    ): ResponseEntity<Any> { // Implement custom response object
        return try {
            val guess = Guess(date, book, chapter)
            val response: Closeness = service.evaluate(guess)
            ResponseEntity.ok(response)

        } catch (e: Exception) {
//            log.error("Some error!") // TODO :: proper err handle
            log.error(e.message)
            ResponseEntity.ok("Some error!")
        }
    }

}