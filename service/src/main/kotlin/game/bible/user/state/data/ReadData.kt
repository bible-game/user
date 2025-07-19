package game.bible.user.state.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Read Request Data
 * @since 26th June 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ReadData (
    val passageKey: String,
    val book: String,
    val chapter: String,
    val verseStart: String,
    val verseEnd: String,

)