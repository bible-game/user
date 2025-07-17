package game.bible.user.state

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Review Request Data
 * @since 17th July 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ReviewData (
    val passageKey: String,
    val date: String,
    val stars: Int,
    val summary: String,
    val answers: List<String>
)