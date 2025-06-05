package game.bible.user.config

import game.bible.config.model.domain.BibleConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/**
 * Exposes user-related Config
 *
 * @author J. R. Smith
 * @since 24th February 2024
 */
@RestController
@RequestMapping("/config")
class ConfigController(private val bible: BibleConfig) {

    /** Returns configured bible information */
    @GetMapping("/bible")
    fun getBibleConfig(): ResponseEntity<Any> { // TODO :: implement custom response object
        return try {
            ResponseEntity.ok(bible)

        } catch (e: Exception) {
            log.error { e.message } // TODO :: implement proper err handle
            ResponseEntity.ok("Some error!")
        }
    }

}