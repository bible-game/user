package game.bible.user

import game.bible.config.model.integration.BibleApiConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/temp")
class TempController(
    private val api: BibleApiConfig,
    private val restClient: RestClient
) { // Note :: temp controller for 0.7 bible completion

    private var cache: Pair<String, String>? = null
    private var default: String? = null

    @GetMapping("/reading/{key}")
    fun getReading(@PathVariable key: String): ResponseEntity<Any> {
        val url = "${api.getBaseUrl()}/$key"

        if (key == "genesis1") {
            if (default.isNullOrEmpty()) {
                default = restClient.get().uri(url).retrieve().body(String::class.java)!!
            }

            log.info { "Using default!" }
            return ResponseEntity.ok(default)
        }

        if (cache != null && cache!!.first == key) {
            log.info { "Using cache!" }
            return ResponseEntity.ok(cache?.second)
        }

        return try {
            cache = Pair(key, restClient.get().uri(url).retrieve().body(String::class.java)!!)
            ResponseEntity.ok((cache?.second))

        } catch (e: Exception) {
            ResponseEntity.ok("Some error!")
        }
    }

}