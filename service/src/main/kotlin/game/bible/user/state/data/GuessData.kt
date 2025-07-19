package game.bible.user.state.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Guess Request Data
 * @since 26th June 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class GuessData (
    val book: String,
    val chapter: String,
    val distance: Int,
    val percentage: Int,
    val passageBook: String,
    val passageChapter: String
)