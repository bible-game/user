package game.bible.user.state

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Read Request Data
 * @since 26th June 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ReadData (
    val passageKey: String
)